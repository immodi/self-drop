package com.projects.selfdrop.interfaces;

public interface IEventPublisher<T> {
    void publish(T item);
}
