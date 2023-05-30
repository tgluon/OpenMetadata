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

package org.openmetadata.service.secrets.converter;

import java.util.Map;
import lombok.Getter;
import org.openmetadata.schema.auth.SSOAuthMechanism;
import org.openmetadata.schema.entity.automations.TestServiceConnectionRequest;
import org.openmetadata.schema.entity.automations.Workflow;
import org.openmetadata.schema.metadataIngestion.DbtPipeline;
import org.openmetadata.schema.metadataIngestion.dbtconfig.DbtGCSConfig;
import org.openmetadata.schema.security.credentials.GCPCredentials;
import org.openmetadata.schema.services.connections.dashboard.SupersetConnection;
import org.openmetadata.schema.services.connections.dashboard.TableauConnection;
import org.openmetadata.schema.services.connections.database.BigQueryConnection;
import org.openmetadata.schema.services.connections.database.DatalakeConnection;
import org.openmetadata.schema.services.connections.database.datalake.GCSConfig;
import org.openmetadata.schema.services.connections.metadata.OpenMetadataConnection;
import org.openmetadata.schema.services.connections.pipeline.AirflowConnection;
import org.openmetadata.schema.services.connections.storage.GcsConnection;

/** Factory class to get a `ClassConverter` based on the service class. */
public final class ClassConverterFactory {
  private ClassConverterFactory() {
    /* Final Class */
  }

  @Getter private static final Map<Class<?>, ClassConverter> converterMap;

  static {
    converterMap =
        Map.ofEntries(
            Map.entry(AirflowConnection.class, new AirflowConnectionClassConverter()),
            Map.entry(BigQueryConnection.class, new BigQueryConnectionClassConverter()),
            Map.entry(DatalakeConnection.class, new DatalakeConnectionClassConverter()),
            Map.entry(DbtGCSConfig.class, new DbtGCSConfigClassConverter()),
            Map.entry(DbtPipeline.class, new DbtPipelineClassConverter()),
            Map.entry(GCSConfig.class, new GCSConfigClassConverter()),
            Map.entry(GCPCredentials.class, new GcsCredentialsClassConverter()),
            Map.entry(GcsConnection.class, new GcsConnectionClassConverter()),
            Map.entry(OpenMetadataConnection.class, new OpenMetadataConnectionClassConverter()),
            Map.entry(SSOAuthMechanism.class, new SSOAuthMechanismClassConverter()),
            Map.entry(SupersetConnection.class, new SupersetConnectionClassConverter()),
            Map.entry(TableauConnection.class, new TableauConnectionClassConverter()),
            Map.entry(TestServiceConnectionRequest.class, new TestServiceConnectionRequestClassConverter()),
            Map.entry(Workflow.class, new WorkflowClassConverter()));
  }

  public static ClassConverter getConverter(Class<?> clazz) {
    return converterMap.getOrDefault(clazz, new DefaultConnectionClassConverter(clazz));
  }
}
