package top.donmor.hyperpad;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.appbar.AppBarLayout;
import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

	//激活矢量图形
	static {
		AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
	}

	//常量
	private static final String
			CHARSET_DEFAULT = "UTF-8",
			KEY_CFG_FILE = "hyper.cfg",
			KEY_CFG_STAT = "status_bar",
			KEY_CFG_WRAP = "wrap",
			KEY_CFG_MONO = "font_mono",
			KEY_CFG_RECENT = "recent",
			KEY_CFG_SIZE = "font_size",
			KEY_FIND_TOAST = "$x",
			KEY_LOG_SDF = "\nhh:mm yyyy/M/d\n",
			KEY_SIS = "sis",
			KEY_SIS_CHANGE = "change",
			KEY_SIS_CURRENT = "uri",
			KEY_SIS_CURRENT_NAME = "name",
			KEY_SIS_ENC = "encoding",
			KEY_SIS_ID = "id",
			KEY_SIS_LB = "line_break",
			KEY_SIS_LEN = "buf",
			KEY_HTML_HEADER1 = "<html><head><title>",
			KEY_HTML_HEADER2 = "</title></head><body>",
			KEY_HTML_HEADER3 = "</body></html>",
			KEY_HTML_P1 = "<p>",
			KEY_HTML_P2 = "</p>",
			KEY_MODIFIED = "* ",
			KEY_SCH_FILE = "file",
			KEY_SCH_CONTENT = "content",
			LINE_COL_KEY = "(",
			LINE_COL_KEY2 = ", ",
			LINE_COL_KEY3 = ")",
			LINE_COL_KEY4 = " ~ (",
			TYPE_HTML = "text/html",
			TYPE_ALL = "*/*";
	private static final int
			BUF_SIZE = 8192,
			OPE_EDIT = 0,
			OPE_NEW = 1,
			OPE_OPEN = 2,
			OPE_RECENT = 3,
			OPE_CLOSE = 4,
			OPE_VIEW = 5,
			OPE_RECEIVE = 6,
			SAF_OPEN = 42,
			SAF_SAVE = 43,
			TAKE_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
	private static final String[] KEY_LEGACY_PERMISSIONS = {Manifest.permission.WRITE_EXTERNAL_STORAGE};

	private enum LINE_BREAK {
		LF("\n"), CR("\r"), CRLF("\r\n");
		private final String s;

		LINE_BREAK(String s) {
			this.s = s;
		}
	}

	private final String[] charsets = Charset.availableCharsets().keySet().toArray(new String[0]);

	//定义关键控件
	private AppBarLayout appbar;
	private Toolbar toolbar;
	private EditorView editor;
	private SharedPreferences preferences;
	private AlertDialog dialog;
	private WebView printWV;

	//初始化变量
	private boolean canUndo = false, canRedo = false, statusBar = false, wrap = false, mono = false;
	private int fontSize = 18, dialogPadding = 30, operation = OPE_EDIT;
	private String encoding = CHARSET_DEFAULT, currentFilename;
	private LINE_BREAK lineBreak = LINE_BREAK.LF;
	private Uri current = null;
	private Uri[] recentUri = new Uri[0];
	private Intent operationData = null;

	//冷启动
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);  //自动主题
		setContentView(R.layout.activity_main);

		//从运行时获取常量
		dialogPadding = (int) (getResources().getDisplayMetrics().density * 20);
		currentFilename = getString(android.R.string.untitled);

		//读配置并应用
		preferences = getSharedPreferences(KEY_CFG_FILE, MODE_PRIVATE);
		statusBar = preferences.getBoolean(KEY_CFG_STAT, false);
		wrap = preferences.getBoolean(KEY_CFG_WRAP, true);
		mono = preferences.getBoolean(KEY_CFG_MONO, false);
		fontSize = preferences.getInt(KEY_CFG_SIZE, 18);
		String[] rSet = Objects.requireNonNull(preferences.getStringSet(KEY_CFG_RECENT, new HashSet<String>())).toArray(new String[0]);
		recentUri = new Uri[rSet.length];
		for (int i = 0; i < rSet.length; i++) recentUri[i] = Uri.parse(rSet[i]);
		//初始化顶栏
		appbar = findViewById(R.id.appbar);
		toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		this.setTitle(R.string.app_name);
		toolbar.setSubtitle(currentFilename);
		//沉浸式双栏
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			Window window = getWindow();
			window.setStatusBarColor(getColor(R.color.design_default_color_primary));
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
				window.setNavigationBarColor(getColor(R.color.design_default_color_primary));
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES)
				window.getInsetsController().setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS | WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS, WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS | WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
			else
				window.getDecorView().setSystemUiVisibility((getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : View.SYSTEM_UI_FLAG_VISIBLE) : View.SYSTEM_UI_FLAG_VISIBLE);
		}
		//编辑器初始化
		editor = findViewById(R.id.editor_view);
		editor.setHorizontallyScrolling(!wrap);
		editor.setTypeface(mono ? Typeface.MONOSPACE : Typeface.DEFAULT);
		editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
		editor.setEditorCallback(new EditorView.EditorCallback() {
			@Override
			public void newDoc() {
				if (editor.isModified()) confirm(OPE_NEW);
				else newDoc();
			}

			@Override
			public void openDoc() {
				if (editor.isModified()) confirm(OPE_OPEN);
				else SAFOpen();
			}

			@Override
			public void saveDoc() {
				if (current == null) SAFSave();
				else fileWrite();
			}

			@Override
			public void printDoc() {
				printText();
			}

			@Override
			public void setCanUndo(boolean val) {
				canUndo = val;
			}

			@Override
			public void setCanRedo(boolean val) {
				canRedo = val;
			}

			//改动状态
			@Override
			public void setModified(boolean val) {
				if (val) toolbar.setSubtitle(KEY_MODIFIED + currentFilename);
				else toolbar.setSubtitle(currentFilename);
			}

			//行列显示
			@Override
			public void selectionChange(int selStart, int selEnd) {
				LinearLayout statBar = appbar.findViewById(R.id.status_bar_widget);
				if (statBar == null) return;
				TextView lineCol = statBar.findViewById(R.id.status_bar_line_col);
				Editable editable = editor.getText();
				if (editable != null) {
					String ws = editable.toString().substring(0, selStart) + LINE_COL_KEY;
					String[] lines = ws.split(LINE_BREAK.LF.s);
					int l = lines.length, c = lines[l - 1].length();
					lineCol.setText(LINE_COL_KEY);
					lineCol.append(String.valueOf(l));
					lineCol.append(LINE_COL_KEY2);
					lineCol.append(String.valueOf(c));
					lineCol.append(LINE_COL_KEY3);
					if (selEnd > selStart) {
						ws = editable.toString().substring(0, selEnd) + LINE_COL_KEY;
						lines = ws.split(LINE_BREAK.LF.s);
						l = lines.length;
						c = lines[l - 1].length();
						lineCol.append(LINE_COL_KEY4);
						lineCol.append(String.valueOf(l));
						lineCol.append(LINE_COL_KEY2);
						lineCol.append(String.valueOf(c));
						lineCol.append(LINE_COL_KEY3);
					}
				} else lineCol.setText(null);
			}
		});
		if (statusBar) loadStatusBar();
		editor.requestFocus();
		String action = getIntent().getAction();
		if (Intent.ACTION_VIEW.equals(action)) viewDoc(getIntent());
		else if (Intent.ACTION_SEND.equals(action)) {
			receiveContent(getIntent());
		}
	}

	//二次启动
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		String action = intent.getAction();

		if (Intent.ACTION_VIEW.equals(action)) {
			if (current != null && current.equals(intent.getData())) return;
			if (editor.isModified()) confirm(OPE_VIEW, intent);
			else viewDoc(intent);
		} else if (Intent.ACTION_SEND.equals(action)) {
			if (editor.isModified()) confirm(OPE_RECEIVE, intent);
			else receiveContent(intent);
		}
	}

	//打开方式
	private void viewDoc(Intent intent) {
		Uri uri = intent.getData();
		if (KEY_SCH_CONTENT.equals(intent.getScheme()) || KEY_SCH_FILE.equals(intent.getScheme())) {
			if (uri != null) openDoc(uri);
		}
	}

	//初始化菜单
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		MenuItem undo = menu.findItem(R.id.action_undo),
				redo = menu.findItem(R.id.action_redo),
				cut = menu.findItem(R.id.action_cut),
				copy = menu.findItem(R.id.action_copy),
				stat = menu.findItem(R.id.action_status_bar),
				wrap = menu.findItem(R.id.action_wrap_text),
				mono = menu.findItem(R.id.action_font_monospace);
		boolean canCp = editor.getSelectionStart() != editor.getSelectionEnd();
		if (undo != null) undo.setEnabled(canUndo);
		if (redo != null) redo.setEnabled(canRedo);
		if (cut != null) cut.setEnabled(canCp);
		if (copy != null) copy.setEnabled(canCp);
		if (stat != null) stat.setChecked(statusBar);
		if (wrap != null) wrap.setChecked(this.wrap);
		if (mono != null) mono.setChecked(this.mono);
		return super.onMenuOpened(featureId, menu);
	}

	//菜单项
	@SuppressLint("InflateParams")
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		final int
				idNew = R.id.action_new,
				idOpen = R.id.action_open,
				idRecent = R.id.action_recent,
				idSave = R.id.action_save,
				idSaveAs = R.id.action_save_as,
				idPrint = R.id.action_print,
				idAbout = R.id.action_about,
				idClose = R.id.action_close,
				idUndo = R.id.action_undo,
				idRedo = R.id.action_redo,
				idCut = R.id.action_cut,
				idCopy = R.id.action_copy,
				idPaste = R.id.action_paste,
				idSelectAll = R.id.action_select_all,
				idFind = R.id.action_find,
				idGoto = R.id.action_goto,
				idLineBreak = R.id.action_line_break,
				idEncoding = R.id.action_encoding,
				idStatusBar = R.id.action_status_bar,
				idWrapText = R.id.action_wrap_text,
				idMono = R.id.action_font_monospace,
				idSize = R.id.action_font_size,
				idStat = R.id.action_statistics;
		switch (item.getItemId()) {
			case idNew:
				if (editor.isModified()) confirm(OPE_NEW);
				else newDoc();
				break;
			case idOpen:
				if (editor.isModified()) confirm(OPE_OPEN);
				else SAFOpen();
				break;
			case idRecent:
				if (editor.isModified()) confirm(OPE_RECENT);
				else openRecent();
				break;
			case idSave:
				if (current == null) SAFSave();
				else fileWrite();
				break;
			case idSaveAs:
				SAFSave();
				break;
			case idPrint:
				printText();
				break;
			case idAbout:
				SpannableString spannableString = new SpannableString(getString(R.string.about));
				Linkify.addLinks(spannableString, Linkify.ALL);
				android.app.AlertDialog aboutDialog = new android.app.AlertDialog.Builder(this)
						.setTitle(R.string.action_about)
						.setMessage(spannableString)
						.show();
				((TextView) aboutDialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
					((TextView) aboutDialog.findViewById(android.R.id.message)).setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Widget_TextView);
				break;
			case idClose:
				onBackPressed();
				break;
			case idUndo:
				editor.undo();
				break;
			case idRedo:
				editor.redo();
				break;
			case idCut:
				editor.onTextContextMenuItem(android.R.id.cut);
				break;
			case idCopy:
				editor.onTextContextMenuItem(android.R.id.copy);
				break;
			case idPaste:
				editor.onTextContextMenuItem(android.R.id.paste);
				break;
			case idSelectAll:
				editor.onTextContextMenuItem(android.R.id.selectAll);
				break;
			case idFind:    //查找替换
				if (appbar.findViewById(R.id.find_replace_widget) == null) {
					appbar.addView(LayoutInflater.from(this).inflate(R.layout.find_replace, null), 1);
					final EditText find = findViewById(R.id.find_edit_find), replace = findViewById(R.id.find_edit_replace);
					findViewById(R.id.find_replace_sw).setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							findViewById(R.id.find_replace_panel).setVisibility(View.VISIBLE);
							v.setVisibility(View.GONE);
						}
					});
					findViewById(R.id.find_close).setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							appbar.removeViewAt(1);
						}
					});
					findViewById(R.id.find_down).setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							if (!editor.find(find.getText().toString()))
								Toast.makeText(MainActivity.this, R.string.find_not_found, Toast.LENGTH_SHORT).show();
							editor.requestFocus();
						}
					});
					findViewById(R.id.find_up).setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							if (!editor.findUp(find.getText().toString()))
								Toast.makeText(MainActivity.this, R.string.find_not_found, Toast.LENGTH_SHORT).show();
							editor.requestFocus();
						}
					});
					final CheckBox caseS = findViewById(R.id.find_case_sensitive);
					caseS.setChecked(editor.isFindCaseSensitive());
					caseS.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							editor.setFindCaseSensitive(caseS.isChecked());
						}
					});
					findViewById(R.id.find_replace).setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							if (!editor.replace(find.getText().toString(), replace.getText().toString()))
								Toast.makeText(MainActivity.this, R.string.find_not_found, Toast.LENGTH_SHORT).show();
							editor.requestFocus();
						}
					});
					findViewById(R.id.find_replace_all).setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							Toast.makeText(MainActivity.this, getString(R.string.find_replaced).replace(KEY_FIND_TOAST, String.valueOf(editor.replaceAll(find.getText().toString(), replace.getText().toString()))), Toast.LENGTH_SHORT).show();
							editor.requestFocus();
						}
					});
				}
				break;
			case idGoto:
				final EditText line = new EditText(this);
				line.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
				line.setInputType(InputType.TYPE_CLASS_NUMBER);
				line.setHint(R.string.goto_hint);
				LinearLayout layout = new LinearLayout(this);
				layout.setPadding(dialogPadding, 0, dialogPadding, 0);
				layout.addView(line);
				if (dialog != null) dialog.dismiss();
				dialog = new AlertDialog.Builder(this)
						.setTitle(R.string.action_goto)
						.setView(layout)
						.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								if (line.length() == 0) return;
								int x = Integer.parseInt(line.getText().toString());
								if (x <= 0) return;
								String content = editor.getEditableText().toString();
								int p = 0;
								for (int i = 0; i < x - 1; i++) {
									int v = content.indexOf(LINE_BREAK.LF.s, p);
									if (v >= 0) p = v + 1;
								}
								editor.setSelection(p);
							}
						})
						.setOnDismissListener(new DialogInterface.OnDismissListener() {
							@Override
							public void onDismiss(DialogInterface dialog) {
								editor.requestFocus();
								MainActivity.this.dialog = null;
							}
						})
						.create();
				line.setOnEditorActionListener(new TextView.OnEditorActionListener() {
					@Override
					public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
						dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
						return false;
					}
				});
				dialog.show();
				break;
			case idLineBreak:
				final int v = lineBreak == LINE_BREAK.CRLF ? 1 : lineBreak == LINE_BREAK.CR ? 2 : 0;
				if (dialog != null) dialog.dismiss();
				dialog = new AlertDialog.Builder(this)
						.setTitle(R.string.action_line_break)
						.setSingleChoiceItems(R.array.line_breaks, v, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								if (which == v) return;
								lineBreak = which == 1 ? LINE_BREAK.CRLF : which == 2 ? LINE_BREAK.CR : LINE_BREAK.LF;
								dialog.dismiss();
								updateStatusBar();
							}
						})
						.setOnDismissListener(new DialogInterface.OnDismissListener() {
							@Override
							public void onDismiss(DialogInterface dialog) {
								editor.requestFocus();
								MainActivity.this.dialog = null;
							}
						})
						.show();
				break;
			case idEncoding:
				int i = 0, e = 0;
				for (String key : charsets) {
					if (key.equals(encoding)) e = i;
					i++;
				}
				final int v2 = e;
				if (dialog != null) dialog.dismiss();
				dialog = new AlertDialog.Builder(this)
						.setTitle(R.string.action_encoding)
						.setSingleChoiceItems(charsets, v2, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								if (which == v2) return;
								encoding = charsets[which];
								dialog.dismiss();
								updateStatusBar();
							}
						})
						.setOnDismissListener(new DialogInterface.OnDismissListener() {
							@Override
							public void onDismiss(DialogInterface dialog) {
								editor.requestFocus();
								MainActivity.this.dialog = null;
							}
						})
						.show();
				break;
			case idStatusBar:
				statusBar = !item.isChecked();
				item.setChecked(statusBar);
				if (statusBar) loadStatusBar();
				else appbar.removeView(appbar.findViewById(R.id.status_bar_widget));
				preferences.edit().putBoolean(KEY_CFG_STAT, statusBar).apply();
				break;
			case idWrapText:
				wrap = !item.isChecked();
				item.setChecked(wrap);
				editor.setHorizontallyScrolling(!wrap);
				preferences.edit().putBoolean(KEY_CFG_WRAP, wrap).apply();
				break;
			case idMono:
				mono = !item.isChecked();
				item.setChecked(mono);
				editor.setTypeface(mono ? Typeface.MONOSPACE : Typeface.DEFAULT);
				preferences.edit().putBoolean(KEY_CFG_MONO, mono).apply();
				break;
			case idSize:
				final NumberPicker picker = new NumberPicker(this);
				picker.setMinValue(8);
				picker.setMaxValue(36);
				picker.setValue(fontSize);
				picker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
					@Override
					public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
						editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, picker.getValue());
					}
				});
				FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
				params.gravity = Gravity.CENTER;
				FrameLayout layout1 = new FrameLayout(this);
				layout1.setPadding(dialogPadding, 0, dialogPadding, 0);
				layout1.addView(picker, params);
				if (dialog != null) dialog.dismiss();
				dialog = new AlertDialog.Builder(this)
						.setTitle(R.string.action_font_size)
						.setView(layout1)
						.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								fontSize = picker.getValue();
								preferences.edit().putInt(KEY_CFG_SIZE, fontSize).apply();
							}
						})
						.setOnCancelListener(new DialogInterface.OnCancelListener() {
							@Override
							public void onCancel(DialogInterface dialog) {
								editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
							}
						})
						.setOnDismissListener(new DialogInterface.OnDismissListener() {
							@Override
							public void onDismiss(DialogInterface dialog) {
								editor.requestFocus();
								MainActivity.this.dialog = null;
							}
						})
						.show();
				break;
			case idStat:
				int[] stat = editor.statistics(), pan = new int[]{R.string.statistics_char, R.string.statistics_word, R.string.statistics_line};
				int p;
				String value;
				SpannableStringBuilder builder = new SpannableStringBuilder();
				builder.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.content_sub)), 0, 0, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
				for (int i1 = 0; i1 < 3; i1++) {
					builder.append(getString(pan[i1]));
					p = builder.length();
					value = String.valueOf(stat[i1]);
					builder.append(value);
					builder.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.content)), p, p + value.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
					builder.setSpan(new RelativeSizeSpan(1.2f), p, p + value.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
					builder.setSpan(new LeadingMarginSpan.Standard(dialogPadding, 0), p, p + value.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
					builder.setSpan(new StyleSpan(Typeface.MONOSPACE.getStyle()), p, p + value.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
				if (dialog != null) dialog.dismiss();
				dialog = new AlertDialog.Builder(this)
						.setTitle(R.string.action_statistics)
						.setMessage(builder)
						.setPositiveButton(android.R.string.ok, null)
						.setOnDismissListener(new DialogInterface.OnDismissListener() {
							@Override
							public void onDismiss(DialogInterface dialog) {
								editor.requestFocus();
								MainActivity.this.dialog = null;
							}
						})
						.show();
				break;
		}
		return super.onOptionsItemSelected(item);
	}

	//SAF执行结果
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
		super.onActivityResult(requestCode, resultCode, resultData);
		if (resultCode == Activity.RESULT_OK && resultData != null) {
			final Uri uri = resultData.getData();
			if (uri != null)
				switch (requestCode) {
					case SAF_OPEN:
						openDoc(uri);
						break;
					case SAF_SAVE:
						fileWrite(this.operation, uri, this.operationData);
						break;
				}
		}
	}


	@SuppressLint("InflateParams")
	private void loadStatusBar() {
		if (appbar.findViewById(R.id.status_bar_widget) == null) {
			appbar.addView(LayoutInflater.from(this).inflate(R.layout.status_bar, null), appbar.getChildCount());
			updateStatusBar();
		}
	}

	private void updateStatusBar() {
		if (appbar.findViewById(R.id.status_bar_widget) == null) return;
		LinearLayout statBar = appbar.findViewById(R.id.status_bar_widget);
		if (statBar == null) return;
		editor.onSelectionChanged(editor.getSelectionStart(), editor.getSelectionEnd());
		TextView lb = statBar.findViewById(R.id.status_bar_line_break), enc = statBar.findViewById(R.id.status_bar_encoding);
		lb.setText(lineBreak == LINE_BREAK.LF ? R.string.status_lb_lf : lineBreak == LINE_BREAK.CRLF ? R.string.status_lb_crlf : R.string.status_lb_cr);
		enc.setText(encoding);

	}

	//暂存状态，当被清理时
	@Override
	public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);
		if (dialog != null) {
			dialog.dismiss();
			dialog = null;
		}
		String id = UUID.randomUUID().toString();
		File cacheFile = new File(getCacheDir(), id);
		BufferedWriter writer = null;
		try {
			Editable e = editor.getText();
			if (!cacheFile.createNewFile() || e == null) throw new IOException();
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cacheFile), CHARSET_DEFAULT));
			writer.write(e.toString());
			writer.flush();
			if (!preferences.edit()
					.putBoolean(KEY_SIS, true)
					.putBoolean(KEY_SIS_CHANGE, editor.isModified())
					.putString(KEY_SIS_ID, id)
					.putInt(KEY_SIS_LEN, editor.length())
					.putString(KEY_SIS_CURRENT, current != null ? current.toString() : null)
					.putString(KEY_SIS_CURRENT_NAME, currentFilename)
					.putInt(KEY_SIS_LB, lineBreak == LINE_BREAK.CRLF ? 1 : lineBreak == LINE_BREAK.CR ? 2 : 0)
					.putString(KEY_SIS_ENC, encoding)
					.commit())
				throw new IOException();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (writer != null) try {
				writer.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	//状态复原
	@Override
	public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		String id;
		if (!editor.isModified() && preferences.getBoolean(KEY_SIS, false) && (id = preferences.getString(KEY_SIS_ID, null)) != null) {
			File cacheFile = new File(getCacheDir(), id);
			BufferedReader reader = null;
			try {

				reader = new BufferedReader(new InputStreamReader(new FileInputStream(cacheFile), CHARSET_DEFAULT));
				int length;
				StringBuilder builder = new StringBuilder();
				char[] chars = new char[preferences.getInt(KEY_SIS_LEN, Integer.MAX_VALUE)];
				length = reader.read(chars);
				builder.append(chars, 0, length);
				String u = preferences.getString(KEY_SIS_CURRENT, null);
				current = u != null ? Uri.parse(u) : null;
				currentFilename = preferences.getString(KEY_SIS_CURRENT_NAME, getString(android.R.string.untitled));
				int e = preferences.getInt(KEY_SIS_LB, 0);
				lineBreak = e == 1 ? LINE_BREAK.CRLF : e == 2 ? LINE_BREAK.CR : LINE_BREAK.LF;
				encoding = preferences.getString(KEY_SIS_ENC, CHARSET_DEFAULT);
				editor.loadContent(builder);
				if (preferences.getBoolean(KEY_SIS_CHANGE, true)) editor.currentState.index = 1;
				editor.editorCallback.setModified(editor.isModified());
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (reader != null) try {
					reader.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		preferences.edit()
				.remove(KEY_SIS)
				.remove(KEY_SIS_LEN)
				.remove(KEY_SIS_CURRENT)
				.remove(KEY_SIS_CURRENT_NAME)
				.remove(KEY_SIS_ENC)
				.remove(KEY_SIS_ID)
				.remove(KEY_SIS_LB)
				.apply();
	}

	//拦截返回键
	@Override
	public void onBackPressed() {
		if (editor.isModified()) confirm(OPE_CLOSE);
		else {
			if (current != null && editor.isLog())
				fileLog();
			setRecent(current);
			super.onBackPressed();
		}
	}

	private void confirm(final int operation) {
		confirm(operation, null);
	}

	//确认对话框，锁存操作数据
	private void confirm(final int operation, final Intent intent) {
		if (operation == OPE_EDIT) return;
		if (dialog != null) dialog.dismiss();
		dialog = new AlertDialog.Builder(this)
				.setTitle(android.R.string.dialog_alert_title)
				.setMessage(R.string.file_save_confirm)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (current == null) SAFSave(operation, intent);
						else fileWrite(operation, intent);
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.setNeutralButton(R.string.do_not_save, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						processOperation(operation, intent);
					}
				})
				.setOnDismissListener(new DialogInterface.OnDismissListener() {
					@Override
					public void onDismiss(DialogInterface dialog) {
						editor.requestFocus();
						MainActivity.this.dialog = null;
					}
				})
				.show();
	}

	//新空白文档，重载
	private void newDoc() {
		newDoc(null, null);
	}

	//新文档
	private void newDoc(CharSequence content, String fileName) {
		if (current != null && editor.isLog())
			fileLog();
		setRecent(current);
		current = null;
		if (fileName != null && !fileName.isEmpty()) currentFilename = fileName;
		else currentFilename = getString(android.R.string.untitled);
		editor.resetAll();
		if (content != null) editor.loadContent(content);
		lineBreak = LINE_BREAK.LF;
		encoding = CHARSET_DEFAULT;
		updateStatusBar();
		editor.setSelection(0);
		editor.requestFocus();
	}

//    private void openFileUri(Uri uri) {
//
//    }

	private void receiveContent(Intent intent) {
		newDoc(intent.getStringExtra(Intent.EXTRA_TEXT), intent.getStringExtra(Intent.EXTRA_SUBJECT));
	}

	//载入文件
	private void openDoc(Uri uri) {
		BufferedInputStream is = null;
		ByteArrayOutputStream os = null;
		try {
			String filename;
			if (KEY_SCH_CONTENT.equals(uri.getScheme())) {  //判断使用的协议
				is = new BufferedInputStream(Objects.requireNonNull(getContentResolver().openInputStream(uri)));
				filename = Objects.requireNonNull(DocumentFile.fromSingleUri(this, uri)).getName();
			} else if (KEY_SCH_FILE.equals(uri.getScheme())) {
				ActivityCompat.requestPermissions(this, KEY_LEGACY_PERMISSIONS, 101);
				is = new BufferedInputStream(new FileInputStream(uri.getPath()));
				filename = uri.getLastPathSegment();
			} else throw new FileNotFoundException();
			os = new ByteArrayOutputStream(BUF_SIZE);   //读全部数据
			int len = is.available();
			int length, lenTotal = 0;
			byte[] b = new byte[BUF_SIZE];
			while ((length = is.read(b)) != -1) {
				os.write(b, 0, length);
				lenTotal += length;
			}
			os.flush();
			if (lenTotal != len) throw new IOException();
			b = os.toByteArray();
			is.close();
			os.close();
			CharsetDetector detector = new CharsetDetector();   //检测编码
			CharsetMatch[] matches = detector.setText(b).detectAll();
			if (matches == null || matches.length == 0) throw new IOException();
			CharsetMatch match = matches[0];
			if (current != null && editor.isLog()) fileLog();   //处置上一文件并初始化新文件
			setRecent(current);
			current = uri;
			currentFilename = filename;
			encoding = match.getName();
			String content = match.getString();
			lineBreak = content.contains(LINE_BREAK.CRLF.s) ? LINE_BREAK.CRLF : content.contains(LINE_BREAK.CR.s) ? LINE_BREAK.CR : LINE_BREAK.LF;
			CharSequence sequence = trimLB(content);
			editor.loadContent(sequence);
			updateStatusBar();
			if (KEY_SCH_CONTENT.equals(uri.getScheme())) try {  //保持读写权限
				getContentResolver().takePersistableUriPermission(uri, TAKE_FLAGS);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(this, R.string.err_load_file, Toast.LENGTH_SHORT).show();
		} finally {
			if (is != null) try {
				is.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (os != null) try {
				os.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void openRecent() {
		setRecent();
		final String[] items = new String[recentUri.length];
		try {
			for (int i = 0; i < items.length; i++) {
				Uri p = recentUri[i];
				items[i] = URLDecoder.decode(KEY_SCH_CONTENT.equals(p.getScheme()) ? p.getLastPathSegment() : p.getSchemeSpecificPart(), CHARSET_DEFAULT);
			}
			if (dialog != null) dialog.dismiss();
			dialog = new AlertDialog.Builder(this)
					.setTitle(R.string.action_recent)
					.setItems(items, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							openDoc(recentUri[which]);
						}
					})
					.setOnDismissListener(new DialogInterface.OnDismissListener() {
						@Override
						public void onDismiss(DialogInterface dialog) {
							editor.requestFocus();
							MainActivity.this.dialog = null;
						}
					})
					.show();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	private void SAFOpen() {
		startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(TYPE_ALL), SAF_OPEN);
	}

	private void SAFSave() {
		this.operation = OPE_EDIT;
		startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(TYPE_ALL).putExtra(Intent.EXTRA_TITLE, currentFilename), SAF_SAVE);
	}

	private void SAFSave(int operation, Intent data) {
		this.operation = operation;
		this.operationData = data;
		startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(TYPE_ALL).putExtra(Intent.EXTRA_TITLE, currentFilename), SAF_SAVE);
	}

	private void fileWrite() {
		fileWrite(OPE_EDIT, current, null);
	}


	private void fileWrite(int operation, Intent opData) {
		fileWrite(operation, current, opData);
	}

	//写入
	private void fileWrite(int operation, Uri uri, Intent opData) {
		BufferedInputStream is;
		BufferedOutputStream os = null;
		try {
			String filename;
			if (KEY_SCH_CONTENT.equals(uri.getScheme())) {  //判断协议
				os = new BufferedOutputStream(Objects.requireNonNull(getContentResolver().openOutputStream(uri)));
				filename = Objects.requireNonNull(DocumentFile.fromSingleUri(this, uri)).getName();
			} else if (KEY_SCH_FILE.equals(uri.getScheme())) {
				os = new BufferedOutputStream(new FileOutputStream(uri.getPath()));
				filename = uri.getLastPathSegment();
			} else throw new FileNotFoundException();
			Editable e = editor.getText();
			if (e == null) throw new IOException();
			CharSequence s = setLB(e.toString(), lineBreak);    //处理文本并二进制化，写入
			is = new BufferedInputStream(new ByteArrayInputStream(removeZero(Charset.forName(encoding).encode(s.toString()).array())));
			int len = is.available();
			int length, lenTotal = 0;
			byte[] b = new byte[BUF_SIZE];
			while ((length = is.read(b)) != -1) {
				os.write(b, 0, length);
				lenTotal += length;
			}
			os.flush();
			if (lenTotal != len) throw new IOException();
			is.close();
			os.close();
			current = uri;
			currentFilename = filename;
			editor.clearModified();
			if (KEY_SCH_CONTENT.equals(uri.getScheme())) try {  //保持权限
				getContentResolver().takePersistableUriPermission(uri, TAKE_FLAGS);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			processOperation(operation, opData);    //如果存在，继续挂起的操作
		} catch (FileNotFoundException e) { //文件无法访问时另存
			e.printStackTrace();
			SAFSave(operation, opData);
		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(this, R.string.err_write_file, Toast.LENGTH_SHORT).show();
		} finally {
			if (os != null) try {
				os.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	//.LOG时间戳
	private void fileLog() {
		InputStream is = null;
		OutputStream os = null;
		try {   //以当前状态读源文件并追加时间戳保存，无需检测CRLF/编码等
			if (KEY_SCH_CONTENT.equals(current.getScheme()))
				is = new BufferedInputStream(Objects.requireNonNull(getContentResolver().openInputStream(current)));
			else if (KEY_SCH_FILE.equals(current.getScheme()))
				is = new BufferedInputStream(new FileInputStream(current.getPath()));
			else throw new FileNotFoundException();
			os = new ByteArrayOutputStream(BUF_SIZE);
			int len = is.available();
			int length, lenTotal = 0;
			byte[] b = new byte[BUF_SIZE];
			while ((length = is.read(b)) != -1) {
				os.write(b, 0, length);
				lenTotal += length;
			}
			os.flush();
			if (lenTotal != len) throw new IOException();
			b = ((ByteArrayOutputStream) os).toByteArray();
			is.close();
			os.close();
			String content = Charset.forName(encoding).decode(ByteBuffer.wrap(b)).toString();
			CharSequence sequence = trimLB(content);
			if (KEY_SCH_CONTENT.equals(current.getScheme()))
				os = new BufferedOutputStream(Objects.requireNonNull(getContentResolver().openOutputStream(current)));
			else if (KEY_SCH_FILE.equals(current.getScheme()))
				os = new BufferedOutputStream(new FileOutputStream(current.getPath()));
			sequence = setLB(sequence, lineBreak);
			String s = sequence.toString() + new SimpleDateFormat(KEY_LOG_SDF, Locale.ENGLISH).format(new Date(System.currentTimeMillis()));
			is = new BufferedInputStream(new ByteArrayInputStream(removeZero(Charset.forName(encoding).encode(s).array())));
			len = is.available();
			lenTotal = 0;
			b = new byte[BUF_SIZE];
			while ((length = is.read(b)) != -1) {
				os.write(b, 0, length);
				lenTotal += length;
			}
			os.flush();
			if (lenTotal != len) throw new IOException();
			is.close();
			os.close();
			if (KEY_SCH_CONTENT.equals(current.getScheme())) try {
				getContentResolver().takePersistableUriPermission(current, TAKE_FLAGS);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(this, R.string.err_write_file, Toast.LENGTH_SHORT).show();
		} finally {
			if (is != null) try {
				is.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (os != null) try {
				os.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	//保存文件后继续挂起的操作
	private void processOperation(int operation, Intent intent) {
		this.operation = OPE_EDIT;
		this.operationData = null;
		switch (operation) {
			case OPE_NEW:
				newDoc();
				break;
			case OPE_OPEN:
				SAFOpen();
				break;
			case OPE_RECENT:
				openRecent();
				break;
			case OPE_CLOSE:
				if (current != null && editor.isLog())
					fileLog();
				setRecent(current);
				super.onBackPressed();
				break;
			case OPE_VIEW:
				viewDoc(intent);
				break;
			case OPE_RECEIVE:
				receiveContent(intent);
				break;
		}
	}

	private void printText() {
		WebView webView = new WebView(this);
		webView.setWebViewClient(new WebViewClient() {
			@Override
			public boolean shouldOverrideUrlLoading(WebView view, String url) {
				return false;
			}

			@Override
			public void onPageFinished(WebView view, String url) {
				createWebPrintJob(view);
				printWV.stopLoading();
				printWV = null;
			}
		});
		webView.loadDataWithBaseURL(null, getHTML(), TYPE_HTML, CHARSET_DEFAULT, null);
		printWV = webView;
	}

	private void createWebPrintJob(WebView webView) {
		PrintManager printManager = (PrintManager) this.getSystemService(Context.PRINT_SERVICE);
		PrintDocumentAdapter printAdapter = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? webView.createPrintDocumentAdapter(currentFilename) : webView.createPrintDocumentAdapter();
		printManager.print(currentFilename, printAdapter, new PrintAttributes.Builder().build());
	}

	//内部强制转为LF
	private CharSequence trimLB(String c) {
		StringBuilder builder = new StringBuilder(c);
		int index;
		while (true) {
			index = builder.indexOf(LINE_BREAK.CRLF.s);
			if (index < 0) break;
			builder.replace(index, index + 2, LINE_BREAK.LF.s);
		}
		while (true) {
			index = builder.indexOf(LINE_BREAK.CR.s);
			if (index < 0) break;
			builder.replace(index, index + 1, LINE_BREAK.LF.s);
		}
		return builder;
	}

	//写入时转为对应的换行符
	private CharSequence setLB(CharSequence c, LINE_BREAK lineBreak) {
		if (lineBreak == LINE_BREAK.LF) return c;
		StringBuilder builder = new StringBuilder(c);
		int index, p = 0;
		while (true) {
			index = builder.indexOf(LINE_BREAK.LF.s, p);
			if (index < 0) break;
			builder.replace(index, index + 1, lineBreak.s);
			p = index + lineBreak.s.length();
		}
		return builder.toString();
	}

	//检除无效历史记录
	private void setRecent() {
		setRecent(null);
	}

	//操作历史记录
	private void setRecent(Uri uri) {
		//检除无效
		int count = 0;
		Uri[] ux = new Uri[recentUri.length];
		for (Uri u : recentUri) {
			try {
				if (KEY_SCH_CONTENT.equals(u.getScheme()))
					getContentResolver().openInputStream(u);
				else if (KEY_SCH_FILE.equals(u.getScheme()))
					if (u.getPath() == null || !new File(u.getPath()).canRead())
						throw new FileNotFoundException();
				if (!u.equals(uri)) ux[count++] = u;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		//构建新列表，最多10条
		int w = uri != null ? 1 : 0;
		int s = Math.min(10, count + w);
		Uri[] uris = new Uri[s];
		if (w > 0) uris[0] = uri;
		System.arraycopy(ux, 0, uris, w, Math.min(10 - w, count));
		recentUri = uris;
		String[] x = new String[recentUri.length];
		for (int i = 0; i < recentUri.length; i++) x[i] = recentUri[i].toString();
		preferences.edit().putStringSet(KEY_CFG_RECENT, new HashSet<>(Arrays.asList(x))).apply();
	}

	//清除0x00
	private static byte[] removeZero(byte[] b) {
		final byte zero = 0x00;
		while (b[b.length - 1] == zero) {
			byte[] c = new byte[b.length - 1];
			System.arraycopy(b, 0, c, 0, c.length);
			b = c;
		}
		return b;
	}

	//文本转HTML
	private String getHTML() {
		StringBuilder builder = new StringBuilder(KEY_HTML_HEADER1)
				.append(currentFilename)
				.append(KEY_HTML_HEADER2);
		String[] arrP = editor.getEditableText().toString().split(LINE_BREAK.LF.s);
		for (String p : arrP) builder.append(KEY_HTML_P1).append(p).append(KEY_HTML_P2);
		return builder.append(KEY_HTML_HEADER3).toString();
	}
}
