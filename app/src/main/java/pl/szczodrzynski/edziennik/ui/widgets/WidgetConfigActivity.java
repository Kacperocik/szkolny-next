/*
 * Copyright (c) Kuba Szczodrzyński 2020-1-6.
 */

package pl.szczodrzynski.edziennik.ui.widgets;

import static pl.szczodrzynski.edziennik.ext.DataExtensionsKt.filterOutArchived;

import android.app.Activity;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonObject;
import com.mikepenz.iconics.typeface.IIcon;
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial;

import java.util.ArrayList;
import java.util.List;

import kotlin.Unit;
import pl.szczodrzynski.edziennik.App;
import pl.szczodrzynski.edziennik.R;
import pl.szczodrzynski.edziennik.data.db.entity.Profile;
import pl.szczodrzynski.edziennik.data.enums.LoginType;
import pl.szczodrzynski.edziennik.data.enums.Theme;
import pl.szczodrzynski.edziennik.databinding.DialogWidgetConfigBinding;
import pl.szczodrzynski.edziennik.databinding.WidgetProfileDialogItemBinding;
import pl.szczodrzynski.edziennik.ext.GraphicsExtensionsKt;
import pl.szczodrzynski.edziennik.ui.widgets.currentlesson.WidgetCurrentLessonProvider;
import pl.szczodrzynski.edziennik.ui.widgets.luckynumber.WidgetLuckyNumberProvider;
import pl.szczodrzynski.edziennik.ui.widgets.notifications.WidgetNotificationsProvider;
import pl.szczodrzynski.edziennik.ui.widgets.timetable.WidgetTimetableProvider;
import pl.szczodrzynski.edziennik.utils.Colors;
import pl.szczodrzynski.edziennik.utils.TextInputDropDown;
import pl.szczodrzynski.edziennik.utils.models.Date;
import timber.log.Timber;

public class WidgetConfigActivity extends Activity {

    public static final String TAG = "WidgetConfigActivity";
    private App app;
    private int mAppWidgetId;
    private List<Profile> profileList;
    private DialogWidgetConfigBinding b;
    public static final int WIDGET_TIMETABLE = 0;
    public static final int WIDGET_NOTIFICATIONS = 1;
    public static final int WIDGET_LUCKY_NUMBER = 2;
    public static final int WIDGET_CURRENT_LESSON = 3;
    private int widgetType = -1;
    private int profileId = -1;
    private String profileName = null;
    private boolean darkTheme = false;
    private int themeMode = WidgetConfig.THEME_MODE_SYSTEM;
    private int themeColor = -1;
    private boolean colorSubjectBackground = false;
    private boolean appBackground = false;
    private boolean headerSameAsBody = false;
    private boolean useEmoji = false;
    private boolean useThemeIconColor = false;
    private boolean bigStyle;
    private float opacity = 0.8f;

    // Live preview views (simplified widget layouts inflated into the dialog)
    private View previewRoot;
    private GradientDrawable previewHeaderBgDrawable;
    private GradientDrawable previewBodyBgDrawable;
    private View previewHeaderWrap;
    private TextView previewTitle;
    private TextView previewSubtitle;
    private ImageView previewHeaderIcon;
    private ImageView previewIcon;
    private TextView previewNumber;
    private TextView previewCaption;
    private TextView previewProfile;
    private TextView previewSubject;
    private TextView previewClassroom;
    private TextView previewTimeRange;
    private TextView previewTimeLeft;
    private TextView previewEventsLabel;
    private TextView previewEvent;
    private ImageView previewRefresh;
    private ProgressBar previewProgress;

    private static class PreviewRow {
        View root;
        TextView first;
        TextView second;
        TextView third;
    }
    private final List<PreviewRow> previewRows = new ArrayList<>();
    // sample subject names, used for subject color tinting in timetable rows
    private final List<String> previewRowSubjects = new ArrayList<>();

    private static final String[][] TIMETABLE_SAMPLE_ROWS = {
            {"8:00 – 8:45", "Matematyka", "sala 12"},
            {"8:50 – 9:35", "Język polski", "sala 24"},
            {"9:40 – 10:25", "Fizyka", "sala 31"},
    };
    private static final String[][] NOTIFICATIONS_SAMPLE_ROWS = {
            {"Nowa ocena", "5 z matematyki", "08:12"},
            {"Nowa wiadomość", "Zebranie z rodzicami", "07:45"},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawable(new ColorDrawable(0));

        app = (App) getApplication();
        if (app == null)
            return;

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        setResult(RESULT_CANCELED);
        if (extras != null) {
            mAppWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

            String className = AppWidgetManager.getInstance(app).getAppWidgetInfo(mAppWidgetId).provider.getClassName();
            if (className.contains("WidgetTimetable"))
                widgetType = WIDGET_TIMETABLE;
            else if (className.contains("WidgetNotifications"))
                widgetType = WIDGET_NOTIFICATIONS;
            else if (className.contains("WidgetLuckyNumber"))
                widgetType = WIDGET_LUCKY_NUMBER;
            else if (className.contains("WidgetCurrentLesson"))
                widgetType = WIDGET_CURRENT_LESSON;

            if (widgetType == -1)
                finish();

            if (widgetType == WIDGET_LUCKY_NUMBER)
                opacity = 0.6f;
            else
                opacity = 0.8f;

            AsyncTask.execute(() -> {
                profileList = App.Companion.getDb().profileDao().getAllNow();
                profileList = filterOutArchived(profileList);

                if (widgetType == WIDGET_NOTIFICATIONS)
                    this.runOnUiThread(this::configure);
                else
                    this.runOnUiThread(this::selectProfile);
            });
        } else {
            finish();
        }
    }

    private void selectProfile() {
        if (profileList.size() > 1 && widgetType != WIDGET_LUCKY_NUMBER && widgetType != WIDGET_CURRENT_LESSON) {
            profileList.add(
                    new Profile(-1,
                            0,
                            LoginType.TEMPLATE,
                            getString(R.string.widget_config_all_profiles),
                            null,
                            "",
                            "",
                            null,
                            new JsonObject()
                    )
            );
        }

        ListAdapter adapter = new ListAdapter() {
            @Override public boolean areAllItemsEnabled() { return true; }
            @Override public boolean isEnabled(int position) { return true; }
            @Override public void registerDataSetObserver(DataSetObserver observer) { }
            @Override public void unregisterDataSetObserver(DataSetObserver observer) { }
            @Override public boolean hasStableIds() { return true; }
            @Override public int getItemViewType(int position) { return 0; }
            @Override public int getViewTypeCount() { return 1; }
            @Override public boolean isEmpty() { return false; }

            @Override public int getCount() { return profileList.size(); }
            @Override public Object getItem(int position) { return profileList.get(position); }

            @Override
            public long getItemId(int position) {
                return profileList.get(position).getId();
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                WidgetProfileDialogItemBinding b;
                if (convertView == null) {
                    b = WidgetProfileDialogItemBinding.inflate(getLayoutInflater(), null, false);
                }
                else {
                    b = WidgetProfileDialogItemBinding.bind(convertView);
                }
                Profile profile = profileList.get(position);

                b.name.setText(profile.getName());
                b.subname.setText(profile.getSubname());
                b.subname.setVisibility(profile.getSubname() == null ? View.GONE : View.VISIBLE);
                b.image.setVisibility(profile.getId() == -1 ? View.GONE : View.VISIBLE);
                if (profile.getId() == -1)
                    b.image.setImageDrawable(null);
                else
                    b.image.setImageDrawable(profile.getImageDrawable(WidgetConfigActivity.this));

                b.getRoot().setOnClickListener(v -> {
                    profileId = profile.getId();
                    profileName = profile.getName();
                    configure();
                });

                return b.getRoot();
            }
        };

        pl.szczodrzynski.edziennik.ext.DialogExtensionsKt.allowTallerHeight(new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.choose_profile)
                .setAdapter(adapter, null)
                .setOnDismissListener(dialog -> finish())
                .show());
    }

    private void configure() {
        b = DialogWidgetConfigBinding.inflate(getLayoutInflater(), null, false);

        pl.szczodrzynski.edziennik.ext.DialogExtensionsKt.allowTallerHeight(new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.widget_config_activity_customize)
                .setView(b.getRoot())
                .setOnDismissListener(dialog -> finish())
                .setPositiveButton(R.string.ok, ((dialog, which) -> {
                    WidgetConfig config = new WidgetConfig(profileId, bigStyle, themeMode, themeColor, colorSubjectBackground, headerSameAsBody, appBackground, opacity);
                    config.useEmoji = useEmoji;
                    config.useThemeIconColor = useThemeIconColor;
                    JsonObject configs = app.getConfig().getWidgetConfigs();
                    configs.add(Integer.toString(mAppWidgetId), app.getGson().toJsonTree(config));
                    app.getConfig().setWidgetConfigs(configs);

                    Intent refreshIntent;
                    switch (widgetType) {
                        default:
                        case WIDGET_TIMETABLE:
                            refreshIntent = new Intent(app, WidgetTimetableProvider.class);
                            break;
                        case WIDGET_NOTIFICATIONS:
                            refreshIntent = new Intent(app, WidgetNotificationsProvider.class);
                            break;
                        case WIDGET_LUCKY_NUMBER:
                            refreshIntent = new Intent(app, WidgetLuckyNumberProvider.class);
                            break;
                        case WIDGET_CURRENT_LESSON:
                            refreshIntent = new Intent(app, WidgetCurrentLessonProvider.class);
                            break;
                    }
                    refreshIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                    // TODO: 2019-05-11 updating only one widget does not seem to work
                    //int[] ids = AppWidgetManager.getInstance(app).getAppWidgetIds(new ComponentName(app, WidgetTimetable.class));
                    //refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                    refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{mAppWidgetId});
                    app.sendBroadcast(refreshIntent);

                    Intent resultValue = new Intent();
                    resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
                    setResult(RESULT_OK, resultValue);
                    finish();
                }))
                .setNegativeButton(R.string.cancel, null)
                .show());

        b.setProfileName(profileName);

        WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
        try {
            b.wallpaper.setImageDrawable(wallpaperManager.getDrawable());
        }
        catch (Exception e) {
            Timber.e(e);
        }
        setupTransparencyGrid();

        b.theme.check(R.id.themeSystem);
        b.theme.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.themeDark) {
                themeMode = WidgetConfig.THEME_MODE_DARK;
            } else if (checkedId == R.id.themeLight) {
                themeMode = WidgetConfig.THEME_MODE_LIGHT;
            } else {
                themeMode = WidgetConfig.THEME_MODE_SYSTEM;
            }
            darkTheme = (themeMode == WidgetConfig.THEME_MODE_DARK) || (themeMode == WidgetConfig.THEME_MODE_SYSTEM && (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
            updatePreview();
        });

        b.themeColor.clear();
        b.themeColor.getItems().add(new TextInputDropDown.Item(-1L, getString(R.string.dialog_widget_config_theme_color_app_default), null, null, null));
        for (Theme theme : Theme.values()) {
            if (theme == Theme.DEFAULT && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                continue;
            }
            b.themeColor.getItems().add(new TextInputDropDown.Item((long) theme.ordinal(), getString(theme.getNameRes()), null, theme, null));
        }
        b.themeColor.select(-1L);
        b.themeColor.setOnChangeListener(item -> {
            themeColor = (int) item.getId();
            updatePreview();
            return true;
        });

        boolean isLuckyNumber = widgetType == WIDGET_LUCKY_NUMBER;
        boolean isCurrentLesson = widgetType == WIDGET_CURRENT_LESSON;
        b.colorSubjectBackground.setVisibility(widgetType == WIDGET_TIMETABLE ? View.VISIBLE : View.GONE);
        b.colorSubjectBackground.setChecked(colorSubjectBackground);
        b.appBackground.setChecked(appBackground);
        b.headerSameAsBody.setVisibility(isLuckyNumber || isCurrentLesson ? View.GONE : View.VISIBLE);
        b.useEmoji.setVisibility(isLuckyNumber ? View.VISIBLE : View.GONE);
        b.useEmoji.setChecked(useEmoji);
        b.useEmoji.setOnCheckedChangeListener((buttonView, isChecked) -> { useEmoji = isChecked; updatePreview(); });
        b.useThemeIconColor.setVisibility(isLuckyNumber ? View.VISIBLE : View.GONE);
        b.useThemeIconColor.setChecked(useThemeIconColor);
        b.useThemeIconColor.setOnCheckedChangeListener((buttonView, isChecked) -> { useThemeIconColor = isChecked; updatePreview(); });

        b.colorSubjectBackground.setOnCheckedChangeListener((buttonView, isChecked) -> {
            colorSubjectBackground = isChecked;
            if (isChecked) {
                appBackground = false;
                b.appBackground.setChecked(false);
            }
            updatePreview();
        });

        b.appBackground.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appBackground = isChecked;
            if (isChecked) {
                colorSubjectBackground = false;
                b.colorSubjectBackground.setChecked(false);
            }
            updatePreview();
        });

        b.headerSameAsBody.setChecked(headerSameAsBody);
        b.headerSameAsBody.setOnCheckedChangeListener((buttonView, isChecked) -> { headerSameAsBody = isChecked; updatePreview(); });

        b.bigStyle.setChecked(bigStyle);
        b.bigStyle.setOnCheckedChangeListener(((buttonView, isChecked) -> {
            bigStyle = isChecked;
            updatePreview();
        }));

        b.opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser)
                    return;
                b.opacityText.setText(getString(R.string.widget_config_activity_opacity_format, progress));
                opacity = (float) progress / 100.0f;
                updatePreview();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        int progress = (int)(opacity * 100.0f);
        b.opacity.setProgress(progress);
        b.opacityText.setText(getString(R.string.widget_config_activity_opacity_format, progress));

        inflatePreview();
        updatePreview();
    }

    private static GradientDrawable roundedBackground(float topRadiusPx, float bottomRadiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadii(new float[]{
                topRadiusPx, topRadiusPx, topRadiusPx, topRadiusPx,
                bottomRadiusPx, bottomRadiusPx, bottomRadiusPx, bottomRadiusPx});
        return drawable;
    }

    /**
     * Classic transparency checkerboard behind the preview, so the opacity
     * slider effect is always visible, even without a wallpaper.
     */
    private void setupTransparencyGrid() {
        View gridView = b.previewTransparencyGrid;
        float density = getResources().getDisplayMetrics().density;
        int size = Math.max(1, (int) (8f * density));

        Bitmap tile = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(tile);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        // empty square outline; shared edges overlap exactly between tiles,
        // giving seamless grid lines without any fill
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, density));
        boolean isDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        paint.setColor(isDark ? 0xFF616161 : 0xFFCFCFCF);
        float half = paint.getStrokeWidth() / 2f;
        canvas.drawRect(half, half, size - half, size - half, paint);

        BitmapDrawable background = new BitmapDrawable(getResources(), tile);
        background.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        gridView.setBackground(background);

        final float radiusPx = 16f * density;
        gridView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
        gridView.setClipToOutline(true);
    }

    private void inflatePreview() {
        b.widgetPreviewContainer.removeAllViews();
        previewRows.clear();
        previewRowSubjects.clear();
        previewSubtitle = null;
        previewHeaderIcon = null;
        previewIcon = null;
        previewNumber = null;
        previewCaption = null;
        previewProfile = null;
        previewSubject = null;
        previewClassroom = null;
        previewTimeRange = null;
        previewTimeLeft = null;
        previewEventsLabel = null;
        previewEvent = null;
        previewRefresh = null;
        previewProgress = null;

        float density = getResources().getDisplayMetrics().density;
        if (widgetType == WIDGET_LUCKY_NUMBER) {
            previewRoot = getLayoutInflater().inflate(R.layout.widget_preview_lucky_number, b.widgetPreviewContainer, false);
            b.widgetPreviewContainer.addView(previewRoot);

            View bodyBg = previewRoot.findViewById(R.id.previewBodyBg);
            previewBodyBgDrawable = roundedBackground(16f * density, 16f * density);
            bodyBg.setBackground(previewBodyBgDrawable);

            previewIcon = previewRoot.findViewById(R.id.previewIcon);
            previewNumber = previewRoot.findViewById(R.id.previewNumber);
            previewCaption = previewRoot.findViewById(R.id.previewCaption);
            previewProfile = previewRoot.findViewById(R.id.previewProfile);

            previewNumber.setText("7");
            previewCaption.setText(getString(R.string.home_lucky_number_yours_today));
            previewProfile.setText(profileName != null ? profileName : getString(R.string.widget_config_all_profiles));
            return;
        }
        if (widgetType == WIDGET_CURRENT_LESSON) {
            previewRoot = getLayoutInflater().inflate(R.layout.widget_preview_current_lesson, b.widgetPreviewContainer, false);
            b.widgetPreviewContainer.addView(previewRoot);

            View bodyBg = previewRoot.findViewById(R.id.previewBodyBg);
            previewBodyBgDrawable = roundedBackground(16f * density, 16f * density);
            bodyBg.setBackground(previewBodyBgDrawable);

            previewProfile = previewRoot.findViewById(R.id.previewProfile);
            previewRefresh = previewRoot.findViewById(R.id.previewRefresh);
            previewSubject = previewRoot.findViewById(R.id.previewSubject);
            previewClassroom = previewRoot.findViewById(R.id.previewClassroom);
            previewTimeRange = previewRoot.findViewById(R.id.previewTimeRange);
            previewProgress = previewRoot.findViewById(R.id.previewProgress);
            previewTimeLeft = previewRoot.findViewById(R.id.previewTimeLeft);
            previewEventsLabel = previewRoot.findViewById(R.id.previewEventsLabel);
            previewEvent = previewRoot.findViewById(R.id.previewEvent);

            previewProfile.setText(profileName != null ? profileName : getString(R.string.widget_config_all_profiles));
            previewSubject.setText(TIMETABLE_SAMPLE_ROWS[0][1]);
            previewClassroom.setText(TIMETABLE_SAMPLE_ROWS[0][2]);
            previewTimeRange.setText("8:00 - 8:45");
            previewProgress.setMax(100);
            previewProgress.setProgress(35);
            int sampleMinutes = 12;
            previewTimeLeft.setText(
                    getResources().getQuantityString(R.plurals.time_left_text, sampleMinutes)
                            + " " + getResources().getQuantityString(R.plurals.time_left_minutes, sampleMinutes, sampleMinutes));
            previewEventsLabel.setText(R.string.widget_current_lesson_events);
            previewEvent.setText("Kartkówka: ułamki");
            return;
        }

        int layoutRes = widgetType == WIDGET_TIMETABLE
                ? R.layout.widget_preview_timetable
                : R.layout.widget_preview_notifications;
        previewRoot = getLayoutInflater().inflate(layoutRes, b.widgetPreviewContainer, false);
        b.widgetPreviewContainer.addView(previewRoot);

        View headerBg = previewRoot.findViewById(R.id.previewHeaderBg);
        previewHeaderBgDrawable = roundedBackground(16f * density, 0f);
        headerBg.setBackground(previewHeaderBgDrawable);

        View bodyBg = previewRoot.findViewById(R.id.previewBodyBg);
        previewBodyBgDrawable = roundedBackground(0f, 16f * density);
        bodyBg.setBackground(previewBodyBgDrawable);

        previewHeaderWrap = previewRoot.findViewById(R.id.previewHeaderWrap);
        previewTitle = previewRoot.findViewById(R.id.previewTitle);
        previewHeaderIcon = previewRoot.findViewById(R.id.previewHeaderIcon);
        LinearLayout rowsContainer = previewRoot.findViewById(R.id.previewRows);

        String[][] sampleRows;
        int rowLayoutRes;
        if (widgetType == WIDGET_TIMETABLE) {
            sampleRows = TIMETABLE_SAMPLE_ROWS;
            rowLayoutRes = R.layout.widget_preview_timetable_row;
            previewSubtitle = previewRoot.findViewById(R.id.previewSubtitle);
            previewSubtitle.setText(Date.getToday().getFormattedString());
            previewTitle.setText(profileName != null ? profileName : getString(R.string.widget_timetable_title_unified));
        } else {
            sampleRows = NOTIFICATIONS_SAMPLE_ROWS;
            rowLayoutRes = R.layout.widget_preview_notifications_row;
            previewTitle.setText(getString(R.string.widget_notifications_title));
        }

        for (String[] row : sampleRows) {
            View rowView = getLayoutInflater().inflate(rowLayoutRes, rowsContainer, false);
            rowsContainer.addView(rowView);
            PreviewRow previewRow = new PreviewRow();
            previewRow.root = rowView;
            if (widgetType == WIDGET_TIMETABLE) {
                previewRow.first = rowView.findViewById(R.id.previewRowTime);
                previewRow.second = rowView.findViewById(R.id.previewRowSubject);
                previewRow.third = rowView.findViewById(R.id.previewRowClassroom);
                previewRowSubjects.add(row[1]);
            } else {
                previewRow.first = rowView.findViewById(R.id.previewRowTitle);
                previewRow.second = rowView.findViewById(R.id.previewRowText);
                previewRow.third = rowView.findViewById(R.id.previewRowDate);
            }
            previewRow.first.setText(row[0]);
            previewRow.second.setText(row[1]);
            previewRow.third.setText(row[2]);
            previewRows.add(previewRow);
        }
    }

    private void setTextSizeSp(TextView textView, float sizeSp) {
        if (textView != null) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        }
    }

    private void updatePreview() {
        if (b == null || previewRoot == null) {
            return;
        }
        WidgetConfig config = new WidgetConfig(profileId, bigStyle, themeMode, themeColor, colorSubjectBackground, headerSameAsBody, appBackground, opacity);
        config.useEmoji = useEmoji;
        config.useThemeIconColor = useThemeIconColor;

        WidgetThemeColors colors = WidgetThemeKt.resolveWidgetTheme(app, config);
        boolean isDark = colors.isDark();
        float density = getResources().getDisplayMetrics().density;

        int alphaInt = (int) (Math.max(0f, Math.min(1f, opacity)) * 255f);
        int defaultBodyColor = isDark ? 0xFF1E1F25 : 0xFFF2F4F7;
        int bodyColor = appBackground ? colors.getBackground() : defaultBodyColor;
        int headerColor = headerSameAsBody ? bodyColor : colors.getPrimary();

        if (widgetType == WIDGET_LUCKY_NUMBER) {
            previewBodyBgDrawable.setColor(bodyColor);
            previewBodyBgDrawable.setAlpha(alphaInt);

            int textColor = colors.getOnSurface();
            previewNumber.setTextColor(textColor);
            previewCaption.setTextColor(textColor);
            previewProfile.setTextColor(colors.getSecondary());

            if (useEmoji) {
                previewIcon.setImageResource(R.drawable.emoji_smiling);
            } else {
                int iconColor;
                if (useThemeIconColor) {
                    iconColor = colors.getPrimary();
                } else if (isDark) {
                    iconColor = Color.WHITE;
                } else {
                    iconColor = 0xFF1C1B1F;
                }
                previewIcon.setImageDrawable(GraphicsExtensionsKt.toDrawable(
                        CommunityMaterial.Icon.cmd_emoticon_happy_outline,
                        this,
                        iconColor,
                        null,
                        R.attr.colorOnBackground,
                        bigStyle ? 48 : 36
                ));
            }
            int iconSizePx = (int) ((bigStyle ? 48 : 36) * density);
            previewIcon.getLayoutParams().width = iconSizePx;
            previewIcon.getLayoutParams().height = iconSizePx;
            previewIcon.requestLayout();
            setTextSizeSp(previewNumber, bigStyle ? 56 : 36);
            setTextSizeSp(previewCaption, bigStyle ? 20 : 16);
            return;
        }

        if (widgetType == WIDGET_CURRENT_LESSON) {
            previewBodyBgDrawable.setColor(bodyColor);
            previewBodyBgDrawable.setAlpha(alphaInt);

            previewProfile.setTextColor(colors.getSecondary());
            previewSubject.setTextColor(colors.getOnSurface());
            previewClassroom.setTextColor(colors.getOnSurfaceVariant());
            previewTimeRange.setTextColor(colors.getOnSurfaceVariant());
            previewTimeLeft.setTextColor(colors.getOnSurface());
            previewEventsLabel.setTextColor(colors.getSecondary());
            previewEvent.setTextColor(colors.getOnSurface());

            setTextSizeSp(previewProfile, bigStyle ? 14 : 12);
            setTextSizeSp(previewSubject, bigStyle ? 26 : 20);
            setTextSizeSp(previewClassroom, bigStyle ? 16 : 14);
            setTextSizeSp(previewTimeRange, bigStyle ? 16 : 14);
            setTextSizeSp(previewTimeLeft, bigStyle ? 16 : 14);
            setTextSizeSp(previewEventsLabel, bigStyle ? 14 : 12);
            setTextSizeSp(previewEvent, bigStyle ? 15 : 13);

            previewRefresh.setImageDrawable(GraphicsExtensionsKt.toDrawable(
                    CommunityMaterial.Icon3.cmd_refresh,
                    this,
                    colors.getOnSurface(),
                    null,
                    R.attr.colorOnBackground,
                    bigStyle ? 28 : 20
            ));
            previewProgress.setProgressTintList(ColorStateList.valueOf(colors.getPrimary()));
            previewProgress.setProgressBackgroundTintList(
                    ColorStateList.valueOf(ColorUtils.setAlphaComponent(colors.getPrimary(), 0x40)));
            return;
        }

        previewHeaderBgDrawable.setColor(headerColor);
        previewHeaderBgDrawable.setAlpha(alphaInt);
        previewBodyBgDrawable.setColor(bodyColor);
        previewBodyBgDrawable.setAlpha(alphaInt);

        int titleColor;
        int iconColor;
        if (headerSameAsBody) {
            titleColor = isDark ? 0xFFE6E1E5 : 0xFF1C1B1F;
            iconColor = isDark ? Color.WHITE : 0xFF1C1B1F;
        } else {
            titleColor = Color.WHITE;
            iconColor = Color.WHITE;
        }
        int subtitleColor = headerSameAsBody
                ? (isDark ? 0xFFCAC4D0 : 0xFF49454F)
                : 0xFFD0E4FF;

        boolean big = bigStyle;
        previewTitle.setTextColor(titleColor);
        setTextSizeSp(previewTitle, big ? 20 : 16);
        if (previewSubtitle != null) {
            previewSubtitle.setTextColor(subtitleColor);
            setTextSizeSp(previewSubtitle, big ? 13 : 11);
        }
        ViewGroup.LayoutParams headerParams = previewHeaderWrap.getLayoutParams();
        headerParams.height = (int) ((big ? 64 : 52) * density);
        previewHeaderWrap.setLayoutParams(headerParams);

        IIcon headerIconGlyph = widgetType == WIDGET_TIMETABLE
                ? CommunityMaterial.Icon3.cmd_refresh
                : CommunityMaterial.Icon.cmd_download_outline;
        previewHeaderIcon.setImageDrawable(GraphicsExtensionsKt.toDrawable(
                headerIconGlyph,
                this,
                iconColor,
                null,
                R.attr.colorOnBackground,
                big ? 28 : 20
        ));

        boolean isTimetable = widgetType == WIDGET_TIMETABLE;
        for (int i = 0; i < previewRows.size(); i++) {
            PreviewRow row = previewRows.get(i);
            row.first.setTextColor(colors.getSecondary());
            row.second.setTextColor(colors.getOnSurface());
            row.third.setTextColor(colors.getOnSurfaceVariant());
            if (isTimetable) {
                setTextSizeSp(row.first, big ? 15 : 12);
                setTextSizeSp(row.second, big ? 18 : 14);
                setTextSizeSp(row.third, big ? 14 : 12);
                if (colorSubjectBackground && !appBackground && i < previewRowSubjects.size()) {
                    int subjectColor = 0;
                    try {
                        subjectColor = Colors.stringToMaterialColorCRC(previewRowSubjects.get(i));
                    } catch (Exception ignored) {
                    }
                    if (subjectColor != 0) {
                        row.root.setBackgroundColor(ColorUtils.setAlphaComponent(subjectColor, 0x40));
                    } else {
                        row.root.setBackgroundColor(Color.TRANSPARENT);
                    }
                } else {
                    row.root.setBackgroundColor(Color.TRANSPARENT);
                }
            } else {
                setTextSizeSp(row.first, big ? 15 : 12);
                setTextSizeSp(row.second, big ? 18 : 14);
                setTextSizeSp(row.third, big ? 13 : 11);
            }
        }
    }
}
