/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-3.
 */

package pl.szczodrzynski.edziennik.ui.debug;

import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pl.szczodrzynski.edziennik.App;
import pl.szczodrzynski.edziennik.R;
import timber.log.Timber;

public class DebugFragment extends Fragment {
    App app;
    private final Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        app = (App)getActivity().getApplication();

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_debug, container, false);
    }

    private static class SimpleObj {
        Object value;
    }
    private static Object target;
    private static Field targetField = null;
    private static Object temp;

    private static Object getByPath(App app, Object parent, String path, boolean setGlobal) throws NoSuchMethodException, NoSuchFieldException, InvocationTargetException, IllegalAccessException {
        Object target = parent;
        Field targetField = null;
        List<String> pathList = new ArrayList<>();
        Matcher matcher = Pattern.compile("(.+?(?:\\(.*?\\))?)\\.").matcher(path+".");
        while (matcher.find()) {
            pathList.add(matcher.group(1));
        }
        for (String el: pathList) {
            if (targetField != null) {
                target = targetField.get(target);
                targetField = null;
            }
            if (el.equals("temp")) {
                target = temp;
                continue;
            }
            if (el.endsWith(")")) {
                String methodName = el.substring(0, el.indexOf("("));
                String parameters = el.substring(el.indexOf("(")+1, el.lastIndexOf(")"));
                Class[] paramTypes = new Class[]{};
                Object[] paramValues = new Object[]{};
                if (!parameters.isEmpty()) {
                    List<Class> paramTypeList = new ArrayList<>();
                    List<Object> paramValueList = new ArrayList<>();
                    for (String parameter: parameters.split(",\\s?")) {
                        if (parameter.equals("null")) {
                            paramTypeList.add(null);
                            paramValueList.add(null);
                        }
                        if (parameter.equals("temp") || (parameter.contains(".") && !parameter.startsWith("\""))) {
                            // this parameter is a path
                            if (parameter.startsWith("app.")) {
                                parameter = parameter.replaceFirst("app\\.", "");
                            }
                            Object param = getByPath(app, app, parameter, false);
                            paramTypeList.add(param.getClass());
                            paramValueList.add(param);
                            continue;
                        }
                        String forceType = null;
                        if (parameter.toLowerCase().endsWith("d")) {
                            forceType = "d";
                            parameter = parameter.substring(0, parameter.length()-1);
                        } else if (parameter.toLowerCase().endsWith("l")) {
                            forceType = "l";
                            parameter = parameter.substring(0, parameter.length()-1);
                        } else if (parameter.toLowerCase().endsWith("f")) {
                            forceType = "f";
                            parameter = parameter.substring(0, parameter.length()-1);
                        } else if (parameter.toLowerCase().endsWith("i")) {
                            forceType = "i";
                            parameter = parameter.substring(0, parameter.length()-1);
                        }
                        SimpleObj obj = app.getGson().fromJson("{\"value\":"+parameter+"}", SimpleObj.class);
                        Class type = obj.value.getClass();
                        Object value = obj.value;
                        if ("d".equals(forceType)) {
                            value = Double.valueOf(parameter);
                            type = Double.TYPE;
                        } else if ("l".equals(forceType)) {
                            value = Long.valueOf(parameter);
                            type = Long.TYPE;
                        } else if ("f".equals(forceType)) {
                            value = Float.valueOf(parameter);
                            type = Float.TYPE;
                        } else if ("i".equals(forceType)) {
                            value = Integer.valueOf(parameter);
                            type = Integer.TYPE;
                        }
                        paramTypeList.add(type);
                        paramValueList.add(value);
                    }
                    paramTypes = paramTypeList.toArray(new Class[0]);
                    paramValues = paramValueList.toArray(new Object[0]);
                }
                try {
                    Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
                    method.setAccessible(true);
                    target = method.invoke(target, paramValues);
                }
                catch (NoSuchMethodException e) {
                    Method method = target.getClass().getMethod(methodName, paramTypes);
                    target = method.invoke(target, paramValues);
                }
            }
            else {
                try {
                    targetField = target.getClass().getDeclaredField(el);
                    targetField.setAccessible(true);
                }
                catch (NoSuchFieldException e) {
                    targetField = target.getClass().getField(el);
                }
            }
        }
        if (setGlobal) {
            DebugFragment.target = target;
            DebugFragment.targetField = targetField;
        }
        return target;
    }

    public static String runCommand(App app, String cmdBatch) {
        for (String cmd: cmdBatch.split(";(?:\\r|\\n|\\r\\n|\\n\\r)?")) {
            cmd = cmd.trim();
            if (cmd.isEmpty())
                continue;

            String[] data;
            String path;
            String valueToSet;
            if (cmd.contains(" = ")) {
                data = cmd.replaceFirst("run ", "").split(" = ");
                path = data[0];
                valueToSet = data[1];
            } else {
                path = cmd.replaceFirst("run ", "");
                valueToSet = null;
            }

            target = app;
            try {
                boolean setToTemp = false;
                if (path.startsWith("temp=")) {
                    path = path.replaceFirst("temp=", "");
                    setToTemp = true;
                }
                if (path.startsWith("app.")) {
                    path = path.replaceFirst("app\\.", "");
                }
                getByPath(app, target, path, true);
                // set the value if specified
                if (valueToSet != null) {
                    targetField.set(target, app.getGson().fromJson(valueToSet, targetField.getGenericType()));
                    targetField = null;
                }
                if (targetField != null) {
                    target = targetField.get(target);
                }
                if (setToTemp) {
                    temp = target;
                }
            } catch (Exception e) {
                Timber.e(e);
                return Log.getStackTraceString(e);
            }
        }
        try {
            return app.getGson().toJson(target);
        } catch (Throwable t) {
            Timber.e(t);
            return Log.getStackTraceString(t);
        }
    }

    private static CharSequence highlightJson(String prettyJson) {
        if (prettyJson == null || prettyJson.isEmpty()) {
            return "";
        }
        SpannableStringBuilder spannable = new SpannableStringBuilder(prettyJson);

        Pattern keyPattern = Pattern.compile("\"([^\"\\\\]|\\\\.)*\"(?=\\s*:)");
        Pattern stringPattern = Pattern.compile("\"([^\"\\\\]|\\\\.)*\"(?!\\s*:)");
        Pattern numberPattern = Pattern.compile("\\b-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b");
        Pattern boolNullPattern = Pattern.compile("\\b(true|false|null)\\b");

        int colorKey = 0xFFAB47BC;     // Material Light Purple
        int colorString = 0xFF66BB6A;  // Material Light Green
        int colorNumber = 0xFF42A5F5;  // Material Light Blue
        int colorBool = 0xFFEC407A;    // Material Light Pink

        Matcher keyMatcher = keyPattern.matcher(prettyJson);
        while (keyMatcher.find()) {
            spannable.setSpan(new ForegroundColorSpan(colorKey), keyMatcher.start(), keyMatcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Matcher stringMatcher = stringPattern.matcher(prettyJson);
        while (stringMatcher.find()) {
            spannable.setSpan(new ForegroundColorSpan(colorString), stringMatcher.start(), stringMatcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Matcher numberMatcher = numberPattern.matcher(prettyJson);
        while (numberMatcher.find()) {
            spannable.setSpan(new ForegroundColorSpan(colorNumber), numberMatcher.start(), numberMatcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Matcher boolMatcher = boolNullPattern.matcher(prettyJson);
        while (boolMatcher.find()) {
            spannable.setSpan(new ForegroundColorSpan(colorBool), boolMatcher.start(), boolMatcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return spannable;
    }

    private void displayJson(TextView tvJson, String rawJson) {
        if (rawJson == null) {
            tvJson.setText("");
            return;
        }
        try {
            JsonElement je = JsonParser.parseString(rawJson);
            String pretty = prettyGson.toJson(je);
            tvJson.setText(highlightJson(pretty));
        } catch (Throwable t) {
            tvJson.setText(rawJson);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        getView().findViewById(R.id.debugRegister).setOnClickListener(v -> {
            String text = ((TextInputEditText) getView().findViewById(R.id.runText)).getText().toString();
            TextView tvJson = getView().findViewById(R.id.tv_json);
            String result = runCommand(app, "run " + text);
            displayJson(tvJson, result);
        });
        getView().findViewById(R.id.debugAppconfig).setOnClickListener(v -> {
            TextView tvJson = getView().findViewById(R.id.tv_json);
            try {
                displayJson(tvJson, prettyGson.toJson(app.getConfig().getValues()));
            } catch (Throwable t) {
                Timber.e(t);
                tvJson.setText(Log.getStackTraceString(t));
            }
        });
        getView().findViewById(R.id.debugAppprofile).setOnClickListener(v -> {
            TextView tvJson = getView().findViewById(R.id.tv_json);
            try {
                displayJson(tvJson, prettyGson.toJson(app.getProfile()));
            } catch (Throwable t) {
                Timber.e(t);
                tvJson.setText(Log.getStackTraceString(t));
            }
        });
    }

}
