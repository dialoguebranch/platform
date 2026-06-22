/*
 *
 *                 Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
 *
 *
 *     This material is part of the Dialogue Branch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.dialoguebranch.web.service.storage;

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.file.datalake.*;
import com.azure.storage.file.datalake.models.PathItem;
import com.dialoguebranch.web.service.DlbProperties;
import com.dialoguebranch.web.service.exception.DLBServiceConfigurationException;
import nl.rrd.utils.AppComponents;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The AzureDataLakeStore class is used for handling file transfers to and from an Azure Data Lake
 * that can be configured to act as a back-up for the local file storage of the Dialogue Branch
 * Web Service.
 *
 * @author Harm op den Akker
 */
public class AzureDataLakeStore {

	private final Logger logger = AppComponents.getLogger(getClass().getSimpleName());

	private final DlbProperties dlbProperties;
	private final DataLakeFileSystemClient dataLakeFileSystemClient;
	public static final String AUTHENTICATION_METHOD_SAS = "sas-token";
	public static final String AUTHENTICATION_METHOD_ACCOUNT_KEY = "account-key";

	public AzureDataLakeStore(DlbProperties dlbProperties) throws DLBServiceConfigurationException {

		this.dlbProperties = dlbProperties;
		DlbProperties.AzureDataLake cfg = dlbProperties.getAzureDataLake();

		DataLakeServiceClient dataLakeServiceClient;
		String authMethod = cfg.getAuthenticationMethod();

		if (authMethod.equals(AUTHENTICATION_METHOD_SAS)) {
			dataLakeServiceClient = new DataLakeServiceClientBuilder()
					.endpoint(cfg.getSasAccountUrl())
					.sasToken(cfg.getSasToken())
					.buildClient();
		} else if (authMethod.equals(AUTHENTICATION_METHOD_ACCOUNT_KEY)) {
			StorageSharedKeyCredential sharedKeyCredential =
					new StorageSharedKeyCredential(cfg.getAccountName(), cfg.getAccountKey());

			DataLakeServiceClientBuilder builder = new DataLakeServiceClientBuilder();
			builder.credential(sharedKeyCredential);
			builder.endpoint("https://" + cfg.getAccountName() + ".dfs.core.windows.net");
			dataLakeServiceClient = builder.buildClient();
		} else {
			throw new DLBServiceConfigurationException(
					"Attempting to initialize AzureDataLakeStore, " +
					"but an unknown authentication method '" + authMethod + "' was configured.");
		}

		dataLakeFileSystemClient =
				dataLakeServiceClient.getFileSystemClient(cfg.getFileSystemName());

		if (authMethod.equals(AUTHENTICATION_METHOD_SAS)) {
			logger.info("Successfully initiated Azure Data Lake Client using account URL '{}'" +
					" and file system '{}'.", cfg.getSasAccountUrl(), cfg.getFileSystemName());
		} else if (authMethod.equals(AUTHENTICATION_METHOD_ACCOUNT_KEY)) {
			logger.info("Successfully initiated Azure Data Lake Client for account: '{}'" +
					" and file system '{}'.", cfg.getAccountName(), cfg.getFileSystemName());
		}
	}

	public void writeLoggedDialogueFile(String user, File file) {
		DataLakeDirectoryClient directoryClient =
				dataLakeFileSystemClient.getDirectoryClient(
						DlbProperties.DIRECTORY_NAME_DIALOGUES + "/" + user);
		DataLakeFileClient fileClient = directoryClient.getFileClient(file.getName());
		try {
			fileClient.uploadFromFile(file.getAbsolutePath(), true);
		} catch (UncheckedIOException e) {
			logger.error("Failed to upload dialogue log session '{}' to Azure Data Lake.",
					file.getAbsolutePath());
		}
	}

	public void writeApplicationLogFile(File file) {
		DataLakeDirectoryClient directoryClient =
				dataLakeFileSystemClient.getDirectoryClient(
						DlbProperties.DIRECTORY_NAME_APPLICATION_LOGS);
		DataLakeFileClient fileClient = directoryClient.getFileClient(file.getName());
		try {
			fileClient.uploadFromFile(file.getAbsolutePath(), true);
			logger.info("Successfully uploaded application log '{}' to Azure Data Lake.",
					file.getAbsolutePath());
		} catch (UncheckedIOException e) {
			logger.error("Failed to upload application log '{}' to Azure Data Lake.",
					file.getAbsolutePath());
		}
	}

	public void populateLocalDialogueLogs(String dialogueBranchUser) throws IOException {
		logger.info("Populating local dialogue log folder for user '{}'.", dialogueBranchUser);
		DataLakeDirectoryClient directoryClient =
				dataLakeFileSystemClient.getDirectoryClient(
						DlbProperties.DIRECTORY_NAME_DIALOGUES + "/" + dialogueBranchUser);

		if (directoryClient.exists()) {
			PagedIterable<PathItem> pathItems = directoryClient.listPaths();

			for (PathItem pathItem : pathItems) {
				Path path = Paths.get(dlbProperties.getDataDir() + File.separator
						+ pathItem.getName());
				String fileName = path.getFileName().toString();

				logger.info("Found file on Azure Data Lake for user '{}': {} (file name: '{}').",
						dialogueBranchUser, pathItem.getName(), fileName);

				DataLakeFileClient fileClient =
						dataLakeFileSystemClient.getFileClient(pathItem.getName());

				File localFile = new File(dlbProperties.getDataDir() + File.separator +
						DlbProperties.DIRECTORY_NAME_DIALOGUES + File.separator +
						dialogueBranchUser + File.separator + fileName);

				OutputStream targetStream = new FileOutputStream(localFile);
				fileClient.read(targetStream);
				targetStream.close();
			}
		}
	}
}
