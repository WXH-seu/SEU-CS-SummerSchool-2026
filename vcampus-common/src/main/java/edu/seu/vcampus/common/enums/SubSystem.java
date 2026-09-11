package edu.seu.vcampus.common.enums;

import java.io.Serializable;

/**
 * Business sub-systems of the virtual campus. A sub-system administrator
 * ({@link Role#SUBSYSADMIN}) is granted a subset of these scopes, and the
 * {@code key} of each scope mirrors the same value used by the client's module
 * navigation so that both sides agree on what "can manage sub-system X" means.
 */
public enum SubSystem implements Serializable {
    STUDENT("student", "学籍管理"),
    COURSE("course", "选课系统"),
    LIBRARY("library", "图书馆"),
    STORE("store", "校园商店");

    private final String key;
    private final String displayName;

    SubSystem(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    /** Stable identifier shared by the client navigation and the server policy. */
    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }
}
