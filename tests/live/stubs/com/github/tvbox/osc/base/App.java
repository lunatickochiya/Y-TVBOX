package com.github.tvbox.osc.base;
import android.content.Context;
import java.io.File;
public class App extends Context {
    private static final App INSTANCE = new App();
    public static File externalFiles, internalFiles;
    public static App getInstance() { return INSTANCE; }
    public File getExternalFilesDir(String type) { return externalFiles; }
    public File getFilesDir() { return internalFiles; }
    public File getCacheDir() { return internalFiles; }
    public File getExternalCacheDir() { return externalFiles; }
}
