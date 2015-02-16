package com.byclosure.webcat.context;

import gherkin.deps.net.iharder.Base64;

import java.util.ArrayList;
import java.util.List;

public class Context implements IContext {
    private List<String> screenshots = new ArrayList<String>();

    private static Context instance;

    public static Context getInstance() {
        if(instance == null) {
            instance = new Context();
        }

        return instance;
    }

    @Override
    public void clearScreenshots() {
        screenshots.clear();
    }

    @Override
    public List<String> getScreenshots() {
        return screenshots;
    }

    @Override
    public void addScreenshot(byte[] data) {
        screenshots.add(Base64.encodeBytes(data));
    }

//    @Override
//    public void addData(Object data) {
//
//    }
}
