/*
 *  Copyright 2022 Collate.
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

import { Col, Divider, Row, Skeleton, Typography } from 'antd';
import { AxiosError } from 'axios';
import { isEmpty, isUndefined } from 'lodash';
import React, {
  Fragment,
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { CUSTOM_PROPERTIES_DOCS } from '../../../constants/docs.constants';
import { EntityField } from '../../../constants/Feeds.constants';
import { usePermissionProvider } from '../../../context/PermissionProvider/PermissionProvider';
import {
  OperationPermission,
  ResourceEntity,
} from '../../../context/PermissionProvider/PermissionProvider.interface';
import { ERROR_PLACEHOLDER_TYPE } from '../../../enums/common.enum';
import { EntityTabs, EntityType } from '../../../enums/entity.enum';
import { ChangeDescription, Type } from '../../../generated/entity/type';
import { getTypeByFQN } from '../../../rest/metadataTypeAPI';
import { Transi18next } from '../../../utils/CommonUtils';
import entityUtilClassBase from '../../../utils/EntityUtilClassBase';
import {
  getChangedEntityNewValue,
  getDiffByFieldName,
  getUpdatedExtensionDiffFields,
} from '../../../utils/EntityVersionUtils';
import { showErrorToast } from '../../../utils/ToastUtils';
import ErrorPlaceHolder from '../ErrorWithPlaceholder/ErrorPlaceHolder';
import {
  CustomPropertyProps,
  ExtentionEntities,
  ExtentionEntitiesKeys,
} from './CustomPropertyTable.interface';
import { ExtensionTable } from './ExtensionTable';
import { PropertyValue } from './PropertyValue';

export const CustomPropertyTable = <T extends ExtentionEntitiesKeys>({
  handleExtensionUpdate,
  entityType,
  hasEditAccess,
  className,
  isVersionView,
  hasPermission,
  entityDetails,
  maxDataCap,
  isRenderedInRightPanel = false,
}: CustomPropertyProps<T>) => {
  const { t } = useTranslation();
  const { getEntityPermissionByFqn } = usePermissionProvider();

  const [entityTypeDetail, setEntityTypeDetail] = useState<Type>({} as Type);
  const [entityTypeDetailLoading, setEntityTypeDetailLoading] =
    useState<boolean>(false);

  const [typePermission, setPermission] = useState<OperationPermission>();

  const fetchTypeDetail = async () => {
    setEntityTypeDetailLoading(true);
    try {
      const res = await getTypeByFQN(entityType);

      setEntityTypeDetail(res);
    } catch (err) {
      showErrorToast(err as AxiosError);
    } finally {
      setEntityTypeDetailLoading(false);
    }
  };

  const fetchResourcePermission = async (entityType: EntityType) => {
    setEntityTypeDetailLoading(true);
    try {
      const permission = await getEntityPermissionByFqn(
        ResourceEntity.TYPE,
        entityType
      );

      setPermission(permission);
    } catch (error) {
      showErrorToast(
        t('server.fetch-entity-permissions-error', {
          entity: t('label.resource-permission-lowercase'),
        })
      );
    } finally {
      setEntityTypeDetailLoading(false);
    }
  };

  const onExtensionUpdate = useCallback(
    async (updatedExtension: ExtentionEntities[T]) => {
      if (!isUndefined(handleExtensionUpdate) && entityDetails) {
        const updatedData = {
          ...entityDetails,
          extension: updatedExtension,
        };
        await handleExtensionUpdate(updatedData);
      }
    },
    [entityDetails, handleExtensionUpdate]
  );

  const extensionObject: {
    extensionObject: ExtentionEntities[T];
    addedKeysList?: string[];
  } = useMemo(() => {
    if (isVersionView) {
      const changeDescription = entityDetails?.changeDescription;
      const extensionDiff = getDiffByFieldName(
        EntityField.EXTENSION,
        changeDescription as ChangeDescription
      );

      const newValues = getChangedEntityNewValue(extensionDiff);

      if (extensionDiff.added) {
        const addedFields = JSON.parse(newValues ? newValues : [])[0];
        if (addedFields) {
          return {
            extensionObject: entityDetails?.extension,
            addedKeysList: Object.keys(addedFields),
          };
        }
      }

      if (entityDetails && extensionDiff.updated) {
        return getUpdatedExtensionDiffFields(entityDetails, extensionDiff);
      }
    }

    return { extensionObject: entityDetails?.extension };
  }, [isVersionView, entityDetails?.extension]);

  const viewAllBtn = useMemo(() => {
    const customProp = entityTypeDetail.customProperties ?? [];

    if (
      maxDataCap &&
      customProp.length >= maxDataCap &&
      entityDetails.fullyQualifiedName
    ) {
      return (
        <Link
          to={entityUtilClassBase.getEntityLink(
            entityType,
            entityDetails.fullyQualifiedName,
            EntityTabs.CUSTOM_PROPERTIES
          )}>
          {t('label.view-all')}
        </Link>
      );
    }

    return null;
  }, [
    entityTypeDetail.customProperties,
    entityType,
    entityDetails,
    maxDataCap,
  ]);

  const dataSource = useMemo(() => {
    const customProperties = entityTypeDetail?.customProperties ?? [];

    return Array.isArray(customProperties)
      ? customProperties.slice(0, maxDataCap)
      : [];
  }, [maxDataCap, entityTypeDetail?.customProperties]);

  useEffect(() => {
    if (typePermission?.ViewAll || typePermission?.ViewBasic) {
      fetchTypeDetail();
    }
  }, [typePermission]);

  useEffect(() => {
    fetchResourcePermission(entityType);
  }, [entityType]);

  if (entityTypeDetailLoading) {
    return <Skeleton active />;
  }

  if (!hasPermission) {
    return (
      <div className="flex-center tab-content-height">
        <ErrorPlaceHolder type={ERROR_PLACEHOLDER_TYPE.PERMISSION} />
      </div>
    );
  }

  if (
    isEmpty(entityTypeDetail.customProperties) &&
    isUndefined(entityDetails?.extension) &&
    // in case of right panel, we don't want to show the placeholder
    !isRenderedInRightPanel
  ) {
    return (
      <div className="flex-center tab-content-height">
        <ErrorPlaceHolder
          className={className}
          placeholderText={
            <Transi18next
              i18nKey="message.no-custom-properties-table"
              renderElement={
                <a
                  href={CUSTOM_PROPERTIES_DOCS}
                  rel="noreferrer"
                  target="_blank"
                  title="Custom properties documentation"
                />
              }
              values={{
                docs: t('label.doc-plural-lowercase'),
              }}
            />
          }
        />
      </div>
    );
  }

  return isEmpty(entityTypeDetail.customProperties) &&
    !isUndefined(entityDetails?.extension) ? (
    <ExtensionTable extension={entityDetails?.extension} />
  ) : (
    <>
      {!isEmpty(entityTypeDetail.customProperties) && (
        <>
          <div className="d-flex justify-between m-b-xs">
            <Typography.Text className="right-panel-label">
              {t('label.custom-property-plural')}
            </Typography.Text>
            {viewAllBtn}
          </div>

          {isRenderedInRightPanel ? (
            <>
              {dataSource.map((record, index) => (
                <Fragment key={record.name}>
                  <PropertyValue
                    extension={extensionObject.extensionObject}
                    hasEditPermissions={hasEditAccess}
                    isRenderedInRightPanel={isRenderedInRightPanel}
                    isVersionView={isVersionView}
                    key={record.name}
                    property={record}
                    versionDataKeys={extensionObject.addedKeysList}
                    onExtensionUpdate={onExtensionUpdate}
                  />
                  {index !== dataSource.length - 1 && (
                    <Divider className="m-y-md" />
                  )}
                </Fragment>
              ))}
            </>
          ) : (
            <Row data-testid="custom-properties-card" gutter={[16, 16]}>
              {dataSource.map((record) => (
                <Col key={record.name} span={8}>
                  <PropertyValue
                    extension={extensionObject.extensionObject}
                    hasEditPermissions={hasEditAccess}
                    isRenderedInRightPanel={isRenderedInRightPanel}
                    isVersionView={isVersionView}
                    property={record}
                    versionDataKeys={extensionObject.addedKeysList}
                    onExtensionUpdate={onExtensionUpdate}
                  />
                </Col>
              ))}
            </Row>
          )}
        </>
      )}
    </>
  );
};
