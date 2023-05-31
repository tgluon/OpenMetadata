/*
 *  Copyright 2021 Collate
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.openmetadata.service.util;

import static org.openmetadata.common.utils.CommonUtil.listOf;
import static org.openmetadata.common.utils.CommonUtil.nullOrEmpty;
import static org.openmetadata.schema.auth.SSOAuthMechanism.SsoServiceType.AUTH_0;
import static org.openmetadata.schema.auth.SSOAuthMechanism.SsoServiceType.AZURE;
import static org.openmetadata.schema.auth.SSOAuthMechanism.SsoServiceType.CUSTOM_OIDC;
import static org.openmetadata.schema.auth.SSOAuthMechanism.SsoServiceType.GOOGLE;
import static org.openmetadata.schema.auth.SSOAuthMechanism.SsoServiceType.OKTA;
import static org.openmetadata.schema.entity.teams.AuthenticationMechanism.AuthType.JWT;
import static org.openmetadata.schema.entity.teams.AuthenticationMechanism.AuthType.SSO;
import static org.openmetadata.service.Entity.ADMIN_USER_NAME;

import at.favre.lib.crypto.bcrypt.BCrypt;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.openmetadata.api.configuration.airflow.AuthConfiguration;
import org.openmetadata.schema.api.configuration.pipelineServiceClient.PipelineServiceClientConfiguration;
import org.openmetadata.schema.auth.BasicAuthMechanism;
import org.openmetadata.schema.auth.JWTAuthMechanism;
import org.openmetadata.schema.auth.JWTTokenExpiry;
import org.openmetadata.schema.auth.SSOAuthMechanism;
import org.openmetadata.schema.entity.teams.AuthenticationMechanism;
import org.openmetadata.schema.entity.teams.User;
import org.openmetadata.schema.security.client.OpenMetadataJWTClientConfig;
import org.openmetadata.schema.services.connections.metadata.AuthProvider;
import org.openmetadata.schema.type.EntityReference;
import org.openmetadata.service.Entity;
import org.openmetadata.service.OpenMetadataApplicationConfig;
import org.openmetadata.service.exception.EntityNotFoundException;
import org.openmetadata.service.jdbi3.EntityRepository;
import org.openmetadata.service.jdbi3.UserRepository;
import org.openmetadata.service.resources.teams.RoleResource;
import org.openmetadata.service.security.jwt.JWTTokenGenerator;

@Slf4j
public final class UserUtil {

  public static void addUsers(AuthProvider authProvider, Set<String> adminUsers, String domain, Boolean isAdmin) {
    try {
      for (String username : adminUsers) {
        createOrUpdateUser(authProvider, username, domain, isAdmin);
      }
    } catch (Exception ex) {
      LOG.error("[BootstrapUser] Encountered Exception while bootstrapping admin user", ex);
    }
  }

  private static void createOrUpdateUser(AuthProvider authProvider, String username, String domain, Boolean isAdmin)
      throws IOException {
    UserRepository userRepository = (UserRepository) Entity.getEntityRepository(Entity.USER);
    User updatedUser;
    try {
      // Create Required Fields List
      List<String> fieldList = new ArrayList<>(userRepository.getPatchFields().getFieldList());
      fieldList.add("authenticationMechanism");

      // Fetch Original User, is available
      User originalUser = userRepository.getByName(null, username, new EntityUtil.Fields(fieldList));
      updatedUser = originalUser;

      // Update Auth Mechanism if not present, and send mail to the user
      if (Objects.equals(authProvider.value(), SSOAuthMechanism.SsoServiceType.BASIC.value())) {
        if (originalUser.getAuthenticationMechanism() == null
            || originalUser.getAuthenticationMechanism().equals(new AuthenticationMechanism())) {
          updateUserWithHashedPwd(updatedUser, getPassword());
          EmailUtil.sendInviteMailToAdmin(updatedUser, ADMIN_USER_NAME);
        }
      } else {
        updatedUser.setAuthenticationMechanism(new AuthenticationMechanism());
      }

      // Update the specific fields isAdmin
      updatedUser.setIsAdmin(isAdmin);

      // user email
      updatedUser.setEmail(String.format("%s@%s", username, domain));
    } catch (EntityNotFoundException e) {
      updatedUser = user(username, domain, username).withIsAdmin(isAdmin).withIsEmailVerified(true);
      // Update Auth Mechanism if not present, and send mail to the user
      if (providerType.equals(SSOAuthMechanism.SsoServiceType.BASIC.value())) {
        updateUserWithHashedPwd(updatedUser, getPassword());
        EmailUtil.sendInviteMailToAdmin(updatedUser, ADMIN_USER_NAME);
      }
    }

    // Update the user
    addOrUpdateUser(updatedUser);
  }

  private static String getPassword() {
    try {
      EmailUtil.getInstance().testConnection();
      return PasswordUtil.generateRandomPassword();
    } catch (Exception ex) {
      LOG.info("Password set to Default.");
    }
    return ADMIN_USER_NAME;
  }

  public static void updateUserWithHashedPwd(User user, String pwd) {
    String hashedPwd = BCrypt.withDefaults().hashToString(12, pwd.toCharArray());
    user.setAuthenticationMechanism(
        new AuthenticationMechanism()
            .withAuthType(AuthenticationMechanism.AuthType.BASIC)
            .withConfig(new BasicAuthMechanism().withPassword(hashedPwd)));
  }

  public static User addOrUpdateUser(User user) {
    UserRepository userRepository = (UserRepository) Entity.getEntityRepository(Entity.USER);
    try {
      RestUtil.PutResponse<User> addedUser = userRepository.createOrUpdate(null, user);
      // should not log the user auth details in LOGS
      LOG.debug("Added user entry: {}", addedUser.getEntity().getName());
      return addedUser.getEntity();
    } catch (Exception exception) {
      // In HA set up the other server may have already added the user.
      LOG.debug("Caught exception", exception);
      user.setAuthenticationMechanism(null);
      LOG.debug("User entry: {} already exists.", user.getName());
    }
    return null;
  }

  public static User user(String name, String domain, String updatedBy) {
    return new User()
        .withId(UUID.randomUUID())
        .withName(name)
        .withFullyQualifiedName(name)
        .withEmail(name + "@" + domain)
        .withUpdatedBy(updatedBy)
        .withUpdatedAt(System.currentTimeMillis())
        .withIsBot(false);
  }

  /**
   * This method add auth mechanism in the following way:
   *
   * <ul>
   *   <li>If original user has already an authMechanism, add it to the user
   *   <li>Otherwise:
   *       <ul>
   *         <li>If airflow configuration is 'openmetadata' and server auth provider is not basic, add JWT auth
   *             mechanism from Airflow configuration
   *         <li>Otherwise:
   *             <ul>
   *               <li>If airflow configuration is 'basic', add JWT auth mechanism with a generated token which does not
   *                   expire
   *               <li>Otherwise, add SSO auth mechanism from Airflow configuration
   *             </ul>
   *       </ul>
   * </ul>
   */
  public static User addOrUpdateBotUser(User user, OpenMetadataApplicationConfig openMetadataApplicationConfig) {
    User originalUser = retrieveWithAuthMechanism(user);
    PipelineServiceClientConfiguration pipelineServiceClientConfiguration =
        openMetadataApplicationConfig.getPipelineServiceClientConfiguration();
    AuthenticationMechanism authMechanism = originalUser != null ? originalUser.getAuthenticationMechanism() : null;
    // the user did not have an auth mechanism and auth config is present
    if (authMechanism == null) {
      authMechanism = buildAuthMechanism(JWT, buildJWTAuthMechanism(null, user));
    }
    user.setAuthenticationMechanism(authMechanism);
    user.setDescription(user.getDescription());
    user.setDisplayName(user.getDisplayName());
    return addOrUpdateUser(user);
  }

  private static JWTAuthMechanism buildJWTAuthMechanism(OpenMetadataJWTClientConfig jwtClientConfig, User user) {
    return Objects.isNull(jwtClientConfig) || nullOrEmpty(jwtClientConfig.getJwtToken())
        ? JWTTokenGenerator.getInstance().generateJWTToken(user, JWTTokenExpiry.Unlimited)
        : new JWTAuthMechanism()
            .withJWTToken(jwtClientConfig.getJwtToken())
            .withJWTTokenExpiry(JWTTokenExpiry.Unlimited);
  }

  private static SSOAuthMechanism buildAuthMechanismConfig(
      SSOAuthMechanism.SsoServiceType ssoServiceType, Object config) {
    return new SSOAuthMechanism().withSsoServiceType(ssoServiceType).withAuthConfig(config);
  }

  private static AuthenticationMechanism buildAuthMechanism(AuthenticationMechanism.AuthType authType, Object config) {
    return new AuthenticationMechanism().withAuthType(authType).withConfig(config);
  }

  private static User retrieveWithAuthMechanism(User user) {
    EntityRepository<User> userRepository = (UserRepository) Entity.getEntityRepository(Entity.USER);
    try {
      return userRepository.getByName(null, user.getName(), new EntityUtil.Fields(List.of("authenticationMechanism")));
    } catch (IOException | EntityNotFoundException e) {
      LOG.debug("Bot entity: {} does not exists.", user);
      return null;
    }
  }

  public static List<EntityReference> getRoleForBot(String botName) {
    String botRole;
    switch (botName) {
      case Entity.INGESTION_BOT_NAME:
        botRole = Entity.INGESTION_BOT_ROLE;
        break;
      case Entity.QUALITY_BOT_NAME:
        botRole = Entity.QUALITY_BOT_ROLE;
        break;
      case Entity.PROFILER_BOT_NAME:
        botRole = Entity.PROFILER_BOT_ROLE;
        break;
      default:
        throw new IllegalArgumentException("No role found for the bot " + botName);
    }
    return listOf(RoleResource.getRole(botRole));
  }
}
