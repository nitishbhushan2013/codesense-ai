package com.codesense.service;

public interface StorageService {

  String store(String content);

  String fetch(String blobKey);
}
