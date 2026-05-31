package com.codesense.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "azure")
@Slf4j
public class AzureBlobStorageService implements StorageService {

  @Value("${storage.azure.connection-string}")
  private String connectionString;

  @Value("${storage.azure.container:code-diffs}")
  private String containerName;

  private BlobContainerClient containerClient;

  @PostConstruct
  void init() {
    BlobServiceClient serviceClient =
        new BlobServiceClientBuilder().connectionString(connectionString).buildClient();
    containerClient = serviceClient.getBlobContainerClient(containerName);
    if (!containerClient.exists()) {
      containerClient.create();
      log.info("Created Azure Blob container '{}'", containerName);
    }
    log.info("Azure Blob Storage initialised — container '{}'", containerName);
  }

  @Override
  public String store(String content) {
    String blobKey = UUID.randomUUID() + ".txt";
    BlobClient blob = containerClient.getBlobClient(blobKey);
    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
    blob.upload(new ByteArrayInputStream(bytes), bytes.length, true);
    log.debug("Stored blob {} ({} bytes)", blobKey, bytes.length);
    return blobKey;
  }

  @Override
  public String fetch(String blobKey) {
    BlobClient blob = containerClient.getBlobClient(blobKey);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    blob.downloadStream(out);
    return out.toString(StandardCharsets.UTF_8);
  }
}
