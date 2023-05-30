/*
 *  Copyright 2023 Collate.
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
import { Form, Input, Modal } from 'antd';
import { AxiosError } from 'axios';
import React, { ReactNode, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getCurrentLocaleDate } from 'utils/TimeUtils';
import { showErrorToast } from 'utils/ToastUtils';
import {
  EntityExportModalContextProps,
  ExportData,
} from './EntityExportModelProvider.interface';

const EntityExportModelContext =
  React.createContext<EntityExportModalContextProps>(
    {} as { showModel: (data: ExportData) => void }
  );

export const EntityExportModelProvider = ({
  children,
}: {
  children: ReactNode;
}) => {
  const [form] = Form.useForm();
  const { t } = useTranslation();
  const [exportData, setExportData] = useState<ExportData | null>(null);

  const handleCancel = () => {
    setExportData(null);
  };

  const showModel = (data: ExportData) => {
    setExportData(data);
  };

  const handleSubmit = () => {
    form.submit();
  };

  /**
   * Creates a downloadable file from csv string and download it on users system
   * @param data - csv string
   */
  const handleDownload = (data: string, fileName: string) => {
    const element = document.createElement('a');

    const file = new Blob([data], { type: 'text/plain' });

    element.textContent = 'download-file';
    element.href = URL.createObjectURL(file);
    element.download = `${fileName}.csv`;
    document.body.appendChild(element);
    element.click();

    URL.revokeObjectURL(element.href);
    document.body.removeChild(element);
  };

  const handleExport = async ({ fileName }: { fileName: string }) => {
    if (exportData === null) {
      Promise.reject(`API must be provided to export the data`);

      return;
    }
    try {
      const data = await exportData.onExport(exportData.name);

      handleDownload(data, fileName);
      handleCancel();
    } catch (error) {
      showErrorToast(error as AxiosError);
    }
  };

  useEffect(() => {
    if (exportData) {
      form.setFieldValue(
        'fileName',
        `${exportData.name}_${getCurrentLocaleDate()}`
      );
    }
  }, [exportData]);

  return (
    <EntityExportModelContext.Provider value={{ showModel }}>
      <>
        {children}
        {exportData && (
          <Modal
            centered
            open
            cancelText={t('label.cancel')}
            closable={false}
            data-testid="export-entity-modal"
            maskClosable={false}
            okText={t('label.export')}
            title={exportData.title ?? t('label.export')}
            onCancel={handleCancel}
            onOk={handleSubmit}>
            <Form form={form} layout="vertical" onFinish={handleExport}>
              <Form.Item
                label={`${t('label.entity-name', {
                  entity: t('label.file'),
                })}:`}
                name="fileName">
                <Input addonAfter=".csv" data-testid="file-name-input" />
              </Form.Item>
            </Form>
          </Modal>
        )}
      </>
    </EntityExportModelContext.Provider>
  );
};

export const useEntityExportModalProvider = () =>
  React.useContext<EntityExportModalContextProps>(EntityExportModelContext);
