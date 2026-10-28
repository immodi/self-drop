package com.projects.selfdrop.interfaces;

import com.projects.selfdrop.entities.File;

import java.util.List;
import java.util.Optional;

public interface IFileRepository {
    List<File> findAll();
    List<File> findSince(long seq);
    List<File> findLatest(int n);
    Optional<File> findById(String id);
    boolean existsById(String id);
    void create(File file);
    boolean update(File file);
    boolean deleteById(String id);
}
