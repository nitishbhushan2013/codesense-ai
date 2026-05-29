package com.codesense.service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
@Slf4j
public class LocalStorageService implements StorageService {

  @Value("${storage.local.path:./local-storage/code-diffs}")
  private String basePath;

  @PostConstruct
  void init() throws IOException {
    Files.createDirectories(Paths.get(basePath));
    log.info("Local storage initialised at {}", basePath);
  }

  @Override
  public String store(String content) {
    String blobKey = UUID.randomUUID() + ".txt";
    Path target = Paths.get(basePath, blobKey);
    try {
      Files.writeString(target, content);
      log.debug("Stored blob {} ({} chars)", blobKey, content.length());
      return blobKey;
    } catch (IOException e) {
      throw new RuntimeException("Failed to store blob: " + e.getMessage(), e);
    }
  }

  @Override
  public String fetch(String blobKey) {
    try {
      return Files.readString(Paths.get(basePath, blobKey));
    } catch (IOException e) {
      throw new RuntimeException("Failed to fetch blob " + blobKey + ": " + e.getMessage(), e);
    }
  }
}
