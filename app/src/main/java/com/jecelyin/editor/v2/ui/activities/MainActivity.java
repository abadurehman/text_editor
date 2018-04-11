/*
 * Copyright (C) 2016 Jecelyin Peng <jecelyin@gmail.com>
 *
 * This file is part of 920 Text Editor.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jecelyin.editor.v2.ui.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.afollestad.materialdialogs.folderselector.FolderChooserDialog;
import com.afollestad.materialdialogs.util.DialogUtils;
import com.azeesoft.lib.colorpicker.ColorPickerDialog;
import com.duy.text.editor.R;
import com.jecelyin.android.file_explorer.FileExplorerActivity;
import com.jecelyin.common.utils.IOUtils;
import com.jecelyin.common.utils.L;
import com.jecelyin.common.utils.SysUtils;
import com.jecelyin.common.utils.UIUtils;
import com.jecelyin.editor.v2.Pref;
import com.jecelyin.editor.v2.common.Command;
import com.jecelyin.editor.v2.common.SaveListener;
import com.jecelyin.editor.v2.task.ClusterCommand;
import com.jecelyin.editor.v2.ui.dialog.ChangeThemeDialog;
import com.jecelyin.editor.v2.ui.dialog.CharsetsDialog;
import com.jecelyin.editor.v2.ui.dialog.GotoLineDialog;
import com.jecelyin.editor.v2.ui.dialog.InsertDateTimeDialog;
import com.jecelyin.editor.v2.ui.dialog.LangListDialog;
import com.jecelyin.editor.v2.ui.dialog.RunDialog;
import com.jecelyin.editor.v2.ui.dialog.WrapCharDialog;
import com.jecelyin.editor.v2.ui.editor.EditorDelegate;
import com.jecelyin.editor.v2.ui.manager.MenuManager;
import com.jecelyin.editor.v2.ui.manager.RecentFilesManager;
import com.jecelyin.editor.v2.ui.manager.TabManager;
import com.jecelyin.editor.v2.ui.settings.SettingsActivity;
import com.jecelyin.editor.v2.ui.widget.SymbolBarLayout;
import com.jecelyin.editor.v2.ui.widget.menu.MenuDef;
import com.jecelyin.editor.v2.ui.widget.menu.MenuFactory;
import com.jecelyin.editor.v2.ui.widget.menu.MenuItemInfo;
import com.jecelyin.editor.v2.utils.DBHelper;

import org.gjt.sp.jedit.Catalog;

import java.io.File;
import java.io.InputStream;
import java.util.List;

/**
 * @author Jecelyin Peng <jecelyin@gmail.com>
 */
public class MainActivity extends BaseActivity
        implements MenuItem.OnMenuItemClickListener
        , FolderChooserDialog.FolderCallback
        , SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = MainActivity.class.getName();
    private static final int RC_OPEN_FILE = 1;
    private final static int RC_SAVE = 3;
    private static final int RC_PERMISSION_STORAGE = 2;
    private static final int RC_SETTINGS = 5;

    public Toolbar mToolbar;
    public LinearLayout mLoadingLayout;
    public ViewPager mTabPager;
    public RecyclerView mMenuRecyclerView;
    public DrawerLayout mDrawerLayout;
    public RecyclerView mTabRecyclerView;
    public TextView mVersionTextView;
    public SymbolBarLayout mSymbolBarLayout;

    private TabManager tabManager;

    private Pref pref;
    private ClusterCommand clusterCommand;
    //    TabDrawable tabDrawable;
    private MenuManager menuManager;
    private FolderChooserDialog.FolderCallback findFolderCallback;
    private long mExitTime;

    public static Intent getOpenFileIntent(File file, int offset) {
        Intent intent = new Intent();
        intent.putExtra("file", file.getPath());
        intent.putExtra("offset", offset);
        return intent;
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        try {
            super.onRestoreInstanceState(savedInstanceState);
        } catch (Exception e) {
            L.d(e); //ignore exception: Unmarshalling unknown type code 7602281 at offset 58340
        }
    }

    private void requestWriteExternalStoragePermission() {
        final String[] permissions = new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE
                , Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            UIUtils.showConfirmDialog(this, null, getString(R.string.need_to_enable_read_storage_permissions), new UIUtils.OnClickCallback() {
                @Override
                public void onOkClick() {
                    ActivityCompat.requestPermissions(MainActivity.this, permissions, RC_PERMISSION_STORAGE);
                }

                @Override
                public void onCancelClick() {
                    finish();
                }
            });
        } else {
            ActivityCompat.requestPermissions(MainActivity.this, permissions, RC_PERMISSION_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // Write external store permission requires a restart
        for (int i = 0; i < permissions.length; i++) {
            //Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permissions[i])
            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                requestWriteExternalStoragePermission();
                return;
            }
        }
        start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pref = Pref.getInstance(this);
        MenuManager.init(this);

        setContentView(R.layout.main_activity);

        mToolbar = findViewById(R.id.toolbar);
        mLoadingLayout = findViewById(R.id.loading_layout);
        mTabPager = findViewById(R.id.tab_pager);
        mMenuRecyclerView = findViewById(R.id.menuRecyclerView);
        mDrawerLayout = findViewById(R.id.drawer_layout);
        mTabRecyclerView = findViewById(R.id.tabRecyclerView);
        mVersionTextView = findViewById(R.id.versionTextView);

        mSymbolBarLayout = findViewById(R.id.symbolBarLayout);
        mSymbolBarLayout.setOnSymbolCharClickListener(new SymbolBarLayout.OnSymbolCharClickListener() {
            @Override
            public void onClick(View v, String text) {
                insertText(text);
            }
        });

        setStatusBarColor(mDrawerLayout);

        bindPreferences();
        setScreenOrientation();

        mDrawerLayout.setEnabled(false);
        mDrawerLayout.setScrimColor(Color.TRANSPARENT);

        final String version = SysUtils.getVersionName(this);
        mVersionTextView.setText(version);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED
                ) {
            requestWriteExternalStoragePermission();
        } else {
            start();

            /*if (savedInstanceState == null && pref.isAutoCheckUpdates()) {
                mDrawerLayout.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        new CheckUpgradeTask(getContext()).checkVersion(version);
                    }
                }, 3000);
            }*/
        }
    }

    private void bindPreferences() {
        mDrawerLayout.setKeepScreenOn(pref.isKeepScreenOn());
        mDrawerLayout.setDrawerLockMode(pref.isEnabledDrawers() ? DrawerLayout.LOCK_MODE_UNDEFINED : DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        mSymbolBarLayout.setVisibility(pref.isReadOnly() ? View.GONE : View.VISIBLE);
        //bind other preference
//        pref.getSharedPreferences().registerOnSharedPreferenceChangeListener(this); //不能这样使用，无法监听
//        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
        pref.registerOnSharedPreferenceChangeListener(this);
    }

    /**
     * 注意registerOnSharedPreferenceChangeListener的listeners是使用WeakHashMap引用的
     * 不能直接registerOnSharedPreferenceChangeListener(new ...) 否则可能监听不起作用
     *
     * @param sharedPreferences
     * @param key
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (mToolbar == null)
            return;
        switch (key) {
            case Pref.KEY_KEEP_SCREEN_ON:
                mToolbar.setKeepScreenOn(sharedPreferences.getBoolean(key, false));
                break;
            case Pref.KEY_ENABLE_HIGHLIGHT:
                Command command = new Command(Command.CommandEnum.HIGHLIGHT);
                command.object = pref.isHighlight() ? null : Catalog.DEFAULT_MODE_NAME;
                doClusterCommand(command);
                break;
            case Pref.KEY_SCREEN_ORIENTATION:
                setScreenOrientation();
                break;
            case Pref.KEY_PREF_ENABLE_DRAWERS:
                mDrawerLayout.setDrawerLockMode(pref.isEnabledDrawers() ? DrawerLayout.LOCK_MODE_UNDEFINED : DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
                break;
            case Pref.KEY_READ_ONLY:
                mSymbolBarLayout.setVisibility(pref.isReadOnly() ? View.GONE : View.VISIBLE);
                break;
        }
    }

    private void setScreenOrientation() {
        int orgi = pref.getScreenOrientation();

        if (Pref.SCREEN_ORIENTATION_AUTO == orgi) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        } else if (Pref.SCREEN_ORIENTATION_LANDSCAPE == orgi) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else if (Pref.SCREEN_ORIENTATION_PORTRAIT == orgi) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    private void start() {
        ((ViewGroup) mLoadingLayout.getParent()).removeView(mLoadingLayout);

//                inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        mTabPager.setVisibility(View.VISIBLE);

        initUI();
    }

    private void initUI() {
        mMenuRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mTabRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mDrawerLayout.setEnabled(true);

        initToolbar();

        if (menuManager == null)
            menuManager = new MenuManager(this);

        //系统可能会随时杀掉后台运行的Activity，如果这一切发生，那么系统就会调用onCreate方法，而不调用onNewIntent方法
        processIntent();
    }

    private void initToolbar() {


        Resources res = getResources();

        mToolbar.setNavigationIcon(R.drawable.ic_drawer_raw);
        mToolbar.setNavigationContentDescription(R.string.tab);

        Menu menu = mToolbar.getMenu();
        List<MenuItemInfo> menuItemInfos = MenuFactory.getInstance(this).getToolbarIcon();
        for (MenuItemInfo item : menuItemInfos) {
            MenuItem menuItem = menu.add(MenuDef.GROUP_TOOLBAR, item.getItemId(), Menu.NONE, item.getTitleResId());
            menuItem.setIcon(MenuManager.makeToolbarNormalIcon(res, item.getIconResId()));

            //menuItem.setShortcut()
            menuItem.setOnMenuItemClickListener(this);
            menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        MenuItem menuItem = menu.add(MenuDef.GROUP_TOOLBAR, R.id.m_menu, Menu.NONE, getString(R.string.more_menu));
        menuItem.setIcon(R.drawable.ic_right_menu);
        menuItem.setOnMenuItemClickListener(this);
        menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        tabManager = new TabManager(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        processIntent();
    }

    private void processIntent() {
        try {
            if (!processIntentImpl()) {
                UIUtils.alert(getContext(), getString(R.string.cannt_handle_intent_x, getIntent().toString()));
            }
        } catch (Throwable e) {
            L.e(e);
            UIUtils.alert(getContext(), getString(R.string.handle_intent_x_error, getIntent().toString() + "\n" + e.getMessage()));
        }
    }

    private boolean processIntentImpl() throws Throwable {
        Intent intent = getIntent();
        L.d("intent=" + intent);
        if (intent == null)
            return true; //pass hint

        String action = intent.getAction();
        // action == null if change theme
        if (action == null || Intent.ACTION_MAIN.equals(action)) {
            return true;
        }

        if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_EDIT.equals(action)) {
            if (intent.getScheme().equals("content")) {
                InputStream attachment = getContentResolver().openInputStream(intent.getData());
                try {
                    String text = IOUtils.toString(attachment);
                    openText(text);
                } catch (OutOfMemoryError e) {
                    UIUtils.toast(this, R.string.out_of_memory_error);
                }

                return true;
            } else if (intent.getScheme().equals("file")) {
                Uri mUri = intent.getData();
                String file = mUri != null ? mUri.getPath() : null;
                if (!TextUtils.isEmpty(file)) {
                    openFile(file);
                    return true;
                }
            }

        } else if (Intent.ACTION_SEND.equals(action) && intent.getExtras() != null) {
            Bundle extras = intent.getExtras();
            CharSequence text = extras.getCharSequence(Intent.EXTRA_TEXT);

            if (text != null) {
                openText(text);
                return true;
            } else {
                Object stream = extras.get(Intent.EXTRA_STREAM);
                if (stream != null && stream instanceof Uri) {
                    openFile(((Uri) stream).getPath());
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * @param menuResId
     * @param status    {@link com.jecelyin.editor.v2.ui.widget.menu.MenuDef#STATUS_NORMAL}, {@link com.jecelyin.editor.v2.ui.widget.menu.MenuDef#STATUS_DISABLED}
     */
    public void setMenuStatus(@IdRes int menuResId, int status) {
        MenuItem menuItem = mToolbar.getMenu().findItem(menuResId);
        if (menuItem == null) {
            throw new RuntimeException("Can't find a menu item");
        }
        Drawable icon = menuItem.getIcon();
        if (status == MenuDef.STATUS_DISABLED) {
            menuItem.setEnabled(false);
            menuItem.setIcon(MenuManager.makeToolbarDisabledIcon(icon));
        } else {
            menuItem.setEnabled(true);
            if (menuItem.getGroupId() == MenuDef.GROUP_TOOLBAR) {
                menuItem.setIcon(MenuManager.makeToolbarNormalIcon(icon));
            } else {
                menuItem.setIcon(MenuManager.makeMenuNormalIcon(icon));
            }
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        onMenuClick(item.getItemId());
        return true;
    }

    private void onMenuClick(int id) {
        Command.CommandEnum commandEnum;

        closeMenu();

        switch (id) {
            case R.id.m_new:
                tabManager.newTab();
                break;
            case R.id.m_open:
//                if (L.debug) {
//                    SpeedActivity.startActivity(this);
//                    break;
//                }
                FileExplorerActivity.startPickFileActivity(this, null, RC_OPEN_FILE);
                break;
            case R.id.m_goto_line:
                new GotoLineDialog(this).show();
                break;
            case R.id.m_history:
                RecentFilesManager rfm = new RecentFilesManager(this);
                rfm.setOnFileItemClickListener(new RecentFilesManager.OnFileItemClickListener() {
                    @Override
                    public void onClick(String file, String encoding) {
                        openFile(file, encoding, 0);
                    }
                });
                rfm.show(getContext());
                break;
            case R.id.m_wrap:
                new WrapCharDialog(this).show();
                break;
            case R.id.m_highlight:
                new LangListDialog(this).show();
                break;
            case R.id.m_menu:
                hideSoftInput();

                mDrawerLayout.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mDrawerLayout.openDrawer(GravityCompat.END);
                    }
                }, 200);

                break;
            case R.id.m_save_all:
                commandEnum = Command.CommandEnum.SAVE;
                Command command = new Command(commandEnum);
                command.args.putBoolean(EditorDelegate.KEY_CLUSTER, true);
                command.object = new SaveListener() {

                    @Override
                    public void onSaved() {
                        doNextCommand();
                    }
                };
                doClusterCommand(command);
                break;
            case R.id.m_theme:
                new ChangeThemeDialog(getContext()).show();
                break;
            case R.id.m_fullscreen:
                boolean fullscreenMode = pref.isFullScreenMode();
                pref.setFullScreenMode(!fullscreenMode);
                UIUtils.toast(this, fullscreenMode
                        ? R.string.disabled_fullscreen_mode_message
                        : R.string.enable_fullscreen_mode_message);
                break;
            case R.id.m_readonly:
                boolean readOnly = !pref.isReadOnly();
                pref.setReadOnly(readOnly);
//                mDrawerLayout.setHideBottomDrawer(readOnly);
                doClusterCommand(new Command(Command.CommandEnum.READONLY_MODE));
                break;
            case R.id.m_encoding:
                new CharsetsDialog(this).show();
                break;
            case R.id.m_color:
                if (ensureNotReadOnly()) {
                    final int primaryTextColor = DialogUtils.resolveColor(this, android.R.attr.textColorPrimary);
                    int theme = DialogUtils.isColorDark(primaryTextColor) ? ColorPickerDialog.LIGHT_THEME : ColorPickerDialog.DARK_THEME;
                    ColorPickerDialog colorPickerDialog = ColorPickerDialog.createColorPickerDialog(this, theme);
                    colorPickerDialog.setOnColorPickedListener(new ColorPickerDialog.OnColorPickedListener() {
                        @Override
                        public void onColorPicked(int color, String hexVal) {
                            insertText(hexVal);
                        }
                    });
                    colorPickerDialog.show();
                }
                break;
            case R.id.m_datetime:
                if (ensureNotReadOnly()) {
                    new InsertDateTimeDialog(this).show();
                }
                break;
            case R.id.m_run:
                new RunDialog(this).show();
                break;
            case R.id.m_settings:
                SettingsActivity.startActivity(this, RC_SETTINGS);
                break;
            case R.id.m_exit:
                if (tabManager != null)
                    tabManager.closeAllTabAndExitApp();
                break;
            default:
                commandEnum = MenuFactory.getInstance(this).idToCommandEnum(id);
                if (commandEnum != Command.CommandEnum.NONE)
                    doCommand(new Command(commandEnum));
        }
    }

    private boolean ensureNotReadOnly() {
        boolean readOnly = pref.isReadOnly();
        if (readOnly) {
            UIUtils.toast(this, R.string.readonly_mode_not_support_this_action);
            return false;
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        onMenuClick(R.id.m_menu);
        return false;
    }

    public void closeMenu() {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        }
        if (mDrawerLayout.isDrawerOpen(GravityCompat.END)) {
            mDrawerLayout.closeDrawer(GravityCompat.END);
        }
    }

    @Override
    public void onFolderSelection(@NonNull FolderChooserDialog dialog, @NonNull File file) {
        if (findFolderCallback != null) {
            findFolderCallback.onFolderSelection(dialog, file);
        }
    }

    public void setFindFolderCallback(FolderChooserDialog.FolderCallback findFolderCallback) {
        this.findFolderCallback = findFolderCallback;
    }

    private void hideSoftInput() {
        doCommand(new Command(Command.CommandEnum.HIDE_SOFT_INPUT));
    }

    private void showSoftInput() {
        doCommand(new Command(Command.CommandEnum.SHOW_SOFT_INPUT));
    }

    /**
     * 需要手动回调 {@link #doNextCommand}
     *
     * @param command
     */
    public void doClusterCommand(Command command) {
        clusterCommand = tabManager.getEditorAdapter().makeClusterCommand();
        clusterCommand.setCommand(command);
        clusterCommand.doNextCommand();
    }

    public void doNextCommand() {
        if (clusterCommand == null)
            return;
        clusterCommand.doNextCommand();
    }

    public void doCommand(Command command) {
        clusterCommand = null;

        EditorDelegate editorDelegate = getCurrentEditorDelegate();
        if (editorDelegate != null) {
            editorDelegate.doCommand(command);

            if (command.what == Command.CommandEnum.HIGHLIGHT) {
                mToolbar.setTitle(editorDelegate.getToolbarText());
            }
        }
    }

    private EditorDelegate getCurrentEditorDelegate() {
        if (tabManager == null || tabManager.getEditorAdapter() == null)
            return null;
        return tabManager.getEditorAdapter().getCurrentEditorDelegate();
    }

    public void startOpenFileSelectorActivity(Intent it) {
        startActivityForResult(it, RC_OPEN_FILE);
    }

    public void startPickPathActivity(String path, String encoding) {
        FileExplorerActivity.startPickPathActivity(this, path, encoding, RC_SAVE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK)
            return;
        switch (requestCode) {
            case RC_OPEN_FILE:
                if (data == null)
                    break;
                openFile(FileExplorerActivity.getFile(data), FileExplorerActivity.getFileEncoding(data), data.getIntExtra("offset", 0));
                break;
            case RC_SAVE:
                String file = FileExplorerActivity.getFile(data);
                String encoding = FileExplorerActivity.getFileEncoding(data);
                tabManager.getEditorAdapter().getCurrentEditorDelegate().saveTo(new File(file), encoding);
                break;
            case RC_SETTINGS:
                break;
        }
    }

    private void openText(CharSequence content) {
        if (TextUtils.isEmpty(content))
            return;
        tabManager.newTab(content);
    }

    private void openFile(String file) {
        openFile(file, null, 0);
    }

    public void openFile(String file, String encoding, int offset) {
        if (TextUtils.isEmpty(file))
            return;
        File f = new File(file);
        if (!f.isFile()) {
            UIUtils.toast(this, R.string.file_not_exists);
            return;
        }
        if (!tabManager.newTab(f, offset, encoding))
            return;
        DBHelper.getInstance(this).addRecentFile(file, encoding);
    }

    public void insertText(CharSequence text) {
        if (text == null)
            return;
        Command c = new Command(Command.CommandEnum.INSERT_TEXT);
        c.object = text;
        doCommand(c);
    }

    public TabManager getTabManager() {
        return tabManager;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            if (mDrawerLayout != null) {
                if (mDrawerLayout.isDrawerOpen(Gravity.LEFT)) {
                    mDrawerLayout.closeDrawer(Gravity.LEFT);
                    return true;
                }
                if (mDrawerLayout.isDrawerOpen(Gravity.RIGHT)) {
                    mDrawerLayout.closeDrawer(Gravity.RIGHT);
                    return true;
                }
            }
            if ((System.currentTimeMillis() - mExitTime) > 2000) {
                UIUtils.toast(getContext(), R.string.press_again_will_exit);
                mExitTime = System.currentTimeMillis();
                return true;
            } else {
                return tabManager == null || tabManager.closeAllTabAndExitApp();
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    public String getCurrentLang() {
        EditorDelegate editorDelegate = getCurrentEditorDelegate();
        if (editorDelegate == null)
            return null;

        return editorDelegate.getLang();
    }

    public RecyclerView getMenuRecyclerView() {
        return mMenuRecyclerView;
    }

    public RecyclerView getTabRecyclerView() {
        return mTabRecyclerView;
    }
}
