package com.projects.selfdrop.interfaces;

import com.projects.selfdrop.entities.Chat;

import java.util.List;
import java.util.Optional;

public interface IChatRepository {
    List<Chat> findAll();
    Optional<Chat> findById(String id);
    boolean existsById(String id);
    void create(Chat chat);
    boolean update(Chat chat);
    boolean deleteById(String id);
}
