package com.byclosure.webcat.context;

import java.util.List;

public interface IContext {
    void clearScreenshots();

    List<String> getScreenshots();
    void addScreenshot(byte[] data);
}
