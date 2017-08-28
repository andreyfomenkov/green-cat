GreenCat
=========

**Experimental Instant Run plugin for Android Studio.**

The plugin can be used when [Google's Instant Run](https://developer.android.com/studio/run/index.html#instant-run) is too slow to be instant or stops working at all.</br>
Don't consider GreenCat as a full-replacement build tool for your apps because it has a pretty narrow area of usage.</br>
Please refer to **Known issues** section below.

Check out the list of features supported or to be done:
- [x] Incremental build for .java files
- [x] Devices support with Android 7+
- [x] Supporting Android Studio projects with [Retrolambda](https://github.com/orfjackal/retrolambda) bytecode transformer inside
- [ ] Supporting Android Studio projects with [Desugar](https://developer.android.com/studio/write/java8-support.html) bytecode transformer inside
- [ ] Supporting Android Studio projects with [Kotlin](https://kotlinlang.org) language
- [ ] Annotation processing
- [ ] Incremental build for XML resources (layouts, strings, dimens, etc.)
- [ ] Devices support with Android 5.x and 6.x

Description
-------------------------

Only after the initial launch of your application using **regular** Android Studio's Run... command you will be able to deploy incremental builds using GreenCat. This plugin doesn't prepare the whole project for you but uses .class files generated by Android Studio during initial build.</br>
Then after clicking *Deploy* command GreenCat prepares .java files for build to .class (v52.0) files with the further processing by Retrolambda (to v51/50.0) and packing them into DEX. Only java sources marked as *modified* or *new* by **git status** command will be considered for incremental build.

GreenCat consists of 3 main components:
- *JAR library* - a wheelhorse of the plugin
- *Plugin for Android Studio* - frontend backed by JAR library
- *Code injection* - a small piece of code in your Android project used to load DEX file(s) with incremental changes and inject them into Application's ClassLoader

JAR library consumes 2 property files:
- *greencat.launcher* - descibes application package and launcher Activity
- *greencat.properties* - describes user's local properties (normally should be added to .gitignore)

Plugin setup
------------
**For Android Studio**
1. Download the latest *greencat-plugin.zip* from repository
2. In IDE: File -> Settings -> Plugins -> Install plugin from disk...
3. Restart IDE
4. You should see two additional buttons in your toolbar near Run button:
 **Deploy:** ![Deploy](https://github.com/andreyfomenkov/green-cat/blob/master/plugin/resources/icons/deploy.png)
 **Clean:** ![Clean](https://github.com/andreyfomenkov/green-cat/blob/master/plugin/resources/icons/clean.png)

**For git-based project**
1. Download the latest *greencat.jar* and put into <PROJECT_DIR>/greencat
2. Add the next line to .gitignore: greencat/greencat.properties (this file will be generated only for you)
3. Create configuration file *greencat.launcher* inside <PROJECT_DIR>/greencat
4. Add the next lines with your appropriate values into <PROJECT_DIR>/greencat/greencat.launcher</br>
```java
PACKAGE=com.your.project.package
LAUNCHER_ACTIVITY=com.your.project.package.StartupActivity
```

**For the main project's application class (that extends Application)**
1. Add permission to read DEX files with incremental changes from SDCard:
```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
```
2. Add DEX injection code :warning: Read **Potential security risks** section first :warning:
```java
private boolean inject(PathClassLoader classLoader) {
    try {
        Field field = BaseDexClassLoader.class.getDeclaredField("pathList");
        field.setAccessible(true);
        final Object pathList = field.get(classLoader);
        final Method method = classLoader.getClass().getMethod("addDexPath", String.class);
        method.invoke(classLoader, DEX_FILE_PATH);

        field = pathList.getClass().getDeclaredField("dexElements");
        field.setAccessible(true);
        final Object[] dexElements = (Object[]) field.get(pathList);
        final Object apk = dexElements[0];
        final Object dex = dexElements[1];
        final Object myArray = Array.newInstance(field.getType().getComponentType(), 2);

        Array.set(myArray, 0, dex);
        Array.set(myArray, 1, apk);
        field.set(pathList, myArray);
        log.d("DEX file successfully loaded");
        return true;

    } catch (final Exception e) {
        log.e(e, "Failed to load DEX file");
        return false;
    }
}
```
and somewhere in project's Application class:

```java
@Override
protected void attachBaseContext(final Context base) {
    super.attachBaseContext(base);
    PathClassLoader classLoader = (PathClassLoader) getClassLoader();
    if (inject(classLoader)) {
        ... // DEX injection OK
    } else {
        ... // DEX injection FAILED
    }
}
```


3. Before running Android application with incremental changes please make sure that application has permissions to read from external storage. For Android 6+ read about [runtime permissions](https://developer.android.com/training/permissions/requesting.html).

Potential security risks :warning:
----------------------------------
**Warning! Please keep in mind that code injection can be very dangerous because anybody has an ability to inject malicious code into your application and steal sensitive data, such as passwords, credit card data and ~~browser history~~ :wink:. So, don't forget to disable DEX injection feature for builds on Google Play. I'd suggest to cover this case with tests.**

Known issues
------------

As mentioned before you should build and run your project on Android device prior to start using GreenCat plugin. For some reasons you can get *strange* crashes when playing with your app after deploying incremental changes using the plugin. The origin of problem in bytecode inconsistency between .class files generated by IDE and .class files from GreenCat. This can happen *sometimes* if you add **public** or **protected** methods/variables into existing Java code. If you're facing with this situation just click Cleanup from plugin toolbar and run desired changes with a common Run... action in Android Studio. Currently I'm investigating issues related to bytecode inconsistency.

License
-------
    Copyright 2017 Andrey Fomenkov

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
