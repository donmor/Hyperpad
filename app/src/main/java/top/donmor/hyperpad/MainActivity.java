package top.donmor.hyperpad;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.appbar.AppBarLayout;
import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

	//激活矢量图形
	static {
		AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
	}

	private ActivityResultLauncher<Intent> getChooserOpen, getChooserSave;

	//常量
	private static final String
			CHARSET_DEFAULT = StandardCharsets.UTF_8.name(),
			KEY_CFG_FILE = "hyper.cfg",
			KEY_CFG_STAT = "status_bar",
			KEY_CFG_WRAP = "wrap",
			KEY_CFG_MONO = "font_mono",
			KEY_CFG_RECENT = "recent",
			KEY_CFG_SIZE = "font_size",
			KEY_FD_R = "r",
			KEY_FD_W = "w",
			KEY_FIND_TOAST = "$x",
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
			KEY_URI_RATE = "market://details?id=",
			LINE_COL_KEY = "(",
			LINE_COL_KEY2 = ", ",
			LINE_COL_KEY3 = ")",
			LINE_COL_KEY4 = " ~ (",
			TYPE_HTML = "text/html",
			TYPE_TEXT = "text/plain",
			TYPE_ALL = "*/*";
	private static final int
//			BUF_SIZE = 8192,
			OPE_EDIT = 0,
			OPE_NEW = 1,
			OPE_OPEN = 2,
			OPE_RECENT = 3,
			OPE_CLOSE = 4,
			OPE_VIEW = 5,
			OPE_RECEIVE = 6,
	//			SAF_OPEN = 42,
//			SAF_SAVE = 43,
	TAKE_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
	private Menu optMenu;

	enum LINE_BREAK {
		LF("\n"), CR("\r"), CRLF("\r\n");
		final String s;

		LINE_BREAK(String s) {
			this.s = s;
		}
	}

	private final String[] charsets = Charset.availableCharsets().keySet().toArray(new String[0]);

	//定义关键控件
	private AppBarLayout appbar;
	private Toolbar toolbar;
	private View findReplace = null, statBar = null;
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
		String[] rSet = Objects.requireNonNull(preferences.getStringSet(KEY_CFG_RECENT, new HashSet<>())).toArray(new String[0]);
		recentUri = new Uri[rSet.length];
		for (int i = 0; i < rSet.length; i++) recentUri[i] = Uri.parse(rSet[i]);
		//初始化顶栏
		appbar = findViewById(R.id.appbar);
		toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		this.setTitle(R.string.app_name);
		toolbar.setSubtitle(currentFilename);
		//SAF回调
		getChooserOpen = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
			if (result.getData() != null) {
				final Uri uri = result.getData().getData();
				if (uri != null)
					openDoc(uri);
			}
		});
		getChooserSave = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
			if (result.getData() != null) {
				final Uri uri = result.getData().getData();
				if (uri != null)
					fileWrite(this.operation, uri, this.operationData);
			}
		});
		//沉浸式双栏
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			Window window = getWindow();
			window.setStatusBarColor(getColor(R.color.design_default_color_primary));
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
				window.setNavigationBarColor(getColor(R.color.design_default_color_primary));
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES)
				Objects.requireNonNull(window.getInsetsController()).setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS | WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS, WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS | WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
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
			public void invokeMenu(int id) {
				optMenu.performIdentifierAction(id, 0);
			}

			@Override
			public void newDoc() {
				if (editor.isModified()) confirm(OPE_NEW);
				else MainActivity.this.newDoc();
			}

			@Override
			public void openDoc() {
				if (editor.isModified()) confirm(OPE_OPEN);
				else SAFOpen();
			}

			@Override
			public void save(boolean a) {
				if (a || current == null) SAFSave();
				else fileWrite();
			}

			@Override
			public void printDoc() {
				printText();
			}

			@Override
			public void quit() {
				MainActivity.this.onBackPressed();
			}

			@Override
			public void find(boolean replace) {
				invokeFindReplace(replace);
			}

			@Override
			public void gotoDialog() {
				MainActivity.this.gotoDialog();
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
		optMenu = menu;
		return true;
	}

	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		MenuItem share = menu.findItem(R.id.action_share),
				undo = menu.findItem(R.id.action_undo),
				redo = menu.findItem(R.id.action_redo),
				cut = menu.findItem(R.id.action_cut),
				copy = menu.findItem(R.id.action_copy),
				paste = menu.findItem(R.id.action_paste),
				del = menu.findItem(R.id.action_delete),
				stat = menu.findItem(R.id.action_status_bar),
				wrap = menu.findItem(R.id.action_wrap_text),
				mono = menu.findItem(R.id.action_font_monospace);
		boolean canCp = editor.getSelectionStart() != editor.getSelectionEnd();
		if (share != null) share.setEnabled(editor.length() > 0);
		if (undo != null) undo.setEnabled(canUndo);
		if (redo != null) redo.setEnabled(canRedo);
		if (cut != null) cut.setEnabled(canCp);
		if (copy != null) copy.setEnabled(canCp);
		if (paste != null)
			paste.setEnabled(((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).hasPrimaryClip());
		if (del != null) del.setEnabled(canCp);
		if (stat != null) stat.setChecked(statusBar);
		if (wrap != null) wrap.setChecked(this.wrap);
		if (mono != null) mono.setChecked(this.mono);
		return super.onMenuOpened(featureId, menu);
	}

	//菜单项
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		final int
				idNew = R.id.action_new,
				idOpen = R.id.action_open,
				idRecent = R.id.action_recent,
				idSave = R.id.action_save,
				idSaveAs = R.id.action_save_as,
				idShare = R.id.action_share,
				idPrint = R.id.action_print,
				idAbout = R.id.action_about,
				idClose = R.id.action_close,
				idUndo = R.id.action_undo,
				idRedo = R.id.action_redo,
				idCut = R.id.action_cut,
				idCopy = R.id.action_copy,
				idPaste = R.id.action_paste,
				idDelete = R.id.action_delete,
				idSelectAll = R.id.action_select_all,
				idTimestamp = R.id.action_timestamp,
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
			case idShare:
				Editable editable = editor.getText();
				if (editable != null) startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
						.putExtra(Intent.EXTRA_SUBJECT, currentFilename)
						.putExtra(Intent.EXTRA_TEXT, editable.toString())
						.setType(TYPE_TEXT), null));
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
						.setPositiveButton(android.R.string.ok, null)
						.setNeutralButton(R.string.market, (dialog, which) -> {
							try {
								startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(KEY_URI_RATE + getPackageName())));
							} catch (Exception e) {
								e.printStackTrace();
							}
						})
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
			case idDelete:
				int st = editor.getSelectionStart(), en = editor.getSelectionEnd();
				if (st < en) editor.getEditableText().delete(st, en);
				break;
			case idSelectAll:
				editor.onTextContextMenuItem(android.R.id.selectAll);
				break;
			case idTimestamp:
				editor.insertTimestamp();
				break;
			case idFind:    //查找替换
				invokeFindReplace(false);
				break;
			case idGoto:
				gotoDialog();
				break;
			case idLineBreak:
				final int v = lineBreak == LINE_BREAK.CRLF ? 1 : lineBreak == LINE_BREAK.CR ? 2 : 0;
				if (dialog != null) dialog.dismiss();
				dialog = new AlertDialog.Builder(this)
						.setTitle(R.string.action_line_break)
						.setSingleChoiceItems(R.array.line_breaks, v, (dialog, which) -> {
							if (which == v) return;
							lineBreak = which == 1 ? LINE_BREAK.CRLF : which == 2 ? LINE_BREAK.CR : LINE_BREAK.LF;
							dialog.dismiss();
							updateStatusBar();
						})
						.setOnDismissListener(dialog -> {
							editor.requestFocus();
							MainActivity.this.dialog = null;
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
						.setSingleChoiceItems(charsets, v2, (dialog, which) -> {
							if (which == v2) return;
							encoding = charsets[which];
							dialog.dismiss();
							updateStatusBar();
						})
						.setOnDismissListener(dialog -> {
							editor.requestFocus();
							MainActivity.this.dialog = null;
						})
						.show();
				break;
			case idStatusBar:
				statusBar = !item.isChecked();
				item.setChecked(statusBar);
				if (statusBar) loadStatusBar();
				else {
					((ViewGroup) findViewById(R.id.status_bar_holder)).removeAllViews();
					statBar = null;
				}
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
				picker.setOnValueChangedListener((picker1, oldVal, newVal) -> editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, picker1.getValue()));
				FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
				params.gravity = Gravity.CENTER;
				FrameLayout layout1 = new FrameLayout(this);
				layout1.setPadding(dialogPadding, 0, dialogPadding, 0);
				layout1.addView(picker, params);
				if (dialog != null) dialog.dismiss();
				dialog = new AlertDialog.Builder(this)
						.setTitle(R.string.action_font_size)
						.setView(layout1)
						.setPositiveButton(android.R.string.ok, (dialog, which) -> {
							fontSize = picker.getValue();
							preferences.edit().putInt(KEY_CFG_SIZE, fontSize).apply();
						})
						.setOnCancelListener(dialog -> editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize))
						.setOnDismissListener(dialog -> {
							editor.requestFocus();
							MainActivity.this.dialog = null;
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
						.setOnDismissListener(dialog -> {
							editor.requestFocus();
							MainActivity.this.dialog = null;
						})
						.show();
				break;
		}
		return super.onOptionsItemSelected(item);
	}


	//查找替换
	private void invokeFindReplace(boolean r) {
		if (findReplace == null) {
			final ViewGroup holder = findViewById(R.id.find_replace_holder);
			findReplace = LayoutInflater.from(this).inflate(R.layout.find_replace, holder);
			final EditText find = findViewById(R.id.find_edit_find), replace = findViewById(R.id.find_edit_replace);
			find.setText(editor.getFinding());
			find.addTextChangedListener(new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {
				}

				@Override
				public void afterTextChanged(Editable s) {
					editor.setFinding(s.toString());
				}
			});
			find.setOnKeyListener((v, keyCode, event) -> editor.onKey(keyCode, event, false));
			findViewById(R.id.find_replace_sw).setOnClickListener(v -> {
				findViewById(R.id.find_replace_panel).setVisibility(View.VISIBLE);
				v.setVisibility(View.GONE);
			});
			findViewById(R.id.find_close).setOnClickListener(v -> {
				holder.removeAllViews();
				findReplace = null;
			});
			findViewById(R.id.find_down).setOnClickListener(v -> {
				if (editor.notFound())
					Toast.makeText(MainActivity.this, R.string.find_not_found, Toast.LENGTH_SHORT).show();
			});
			findViewById(R.id.find_up).setOnClickListener(v -> {
				if (editor.notFoundUp())
					Toast.makeText(MainActivity.this, R.string.find_not_found, Toast.LENGTH_SHORT).show();
			});
			final CheckBox caseS = findViewById(R.id.find_case_sensitive);
			caseS.setChecked(editor.isFindCaseSensitive());
			caseS.setOnClickListener(v -> editor.setFindCaseSensitive(caseS.isChecked()));
			findViewById(R.id.find_replace).setOnClickListener(v -> {
				if (!editor.replace(find.getText().toString(), replace.getText().toString()))
					Toast.makeText(MainActivity.this, R.string.find_not_found, Toast.LENGTH_SHORT).show();
				editor.requestFocus();
			});
			findViewById(R.id.find_replace_all).setOnClickListener(v -> {
				Toast.makeText(MainActivity.this, getString(R.string.find_replaced).replace(KEY_FIND_TOAST, String.valueOf(editor.replaceAll(find.getText().toString(), replace.getText().toString()))), Toast.LENGTH_SHORT).show();
				editor.requestFocus();
			});
		}
		if (r) findViewById(R.id.find_replace_sw).callOnClick();

	}

	private void gotoDialog() {
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
				.setPositiveButton(android.R.string.ok, (dialog, which) -> {
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
				})
				.setOnDismissListener(dialog -> {
					editor.requestFocus();
					MainActivity.this.dialog = null;
				})
				.create();
		line.setOnEditorActionListener((v, actionId, event) -> {
			dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
			return false;
		});
		dialog.show();
	}

	private void loadStatusBar() {
		if (appbar.findViewById(R.id.status_bar_widget) == null) {
			statBar = LayoutInflater.from(this).inflate(R.layout.status_bar, findViewById(R.id.status_bar_holder));
			updateStatusBar();
		}
	}

	private void updateStatusBar() {
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
				if (preferences.getBoolean(KEY_SIS_CHANGE, true)) editor.setDirty();
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
				.setPositiveButton(android.R.string.ok, (dialog, which) -> {
					if (current == null) SAFSave(operation, intent);
					else fileWrite(operation, intent);
				})
				.setNegativeButton(android.R.string.cancel, null)
				.setNeutralButton(R.string.do_not_save, (dialog, which) -> processOperation(operation, intent))
				.setOnDismissListener(dialog -> {
					editor.requestFocus();
					MainActivity.this.dialog = null;
				})
				.show();
	}

	//新空白文档，重载
	private void newDoc() {
		newDoc(null, null);
	}

	//新文档
	private void newDoc(CharSequence content, String fileName) {
		setRecent(current);
		current = null;
		currentFilename = fileName != null && !fileName.isEmpty() ? fileName : getString(android.R.string.untitled);
		editor.resetAll();
		if (content != null) editor.loadContent(content);
		lineBreak = LINE_BREAK.LF;
		encoding = CHARSET_DEFAULT;
		updateStatusBar();
		editor.setSelection(0);
		editor.requestFocus();
	}

	private void receiveContent(Intent intent) {
		newDoc(intent.getStringExtra(Intent.EXTRA_TEXT), intent.getStringExtra(Intent.EXTRA_SUBJECT));
	}

	//载入文件
	private void openDoc(Uri uri) {
		try (ParcelFileDescriptor ifd = Objects.requireNonNull(getContentResolver().openFileDescriptor(uri, KEY_FD_R));
				FileInputStream is = new FileInputStream(ifd.getFileDescriptor());
				FileChannel ic = is.getChannel()) {
			String filename;
			if (KEY_SCH_CONTENT.equals(uri.getScheme())) {  //判断使用的协议
				filename = Objects.requireNonNull(DocumentFile.fromSingleUri(this, uri)).getName();
			} else if (KEY_SCH_FILE.equals(uri.getScheme())) {
				checkPermission();
				filename = uri.getLastPathSegment();
			} else throw new FileNotFoundException();
			CharsetDetector detector = new CharsetDetector();   //检测编码
			CharsetMatch[] matches = detector.setText(fc2ba(ic)).detectAll();
			if (matches == null || matches.length == 0) throw new IOException();
			CharsetMatch match = matches[0];
			setRecent(current);   //处置上一文件并初始化新文件
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
//		} finally {
//			if (is != null) try {
//				is.close();
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
//			if (os != null) try {
//				os.close();
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
		}
	}

	//历史记录
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
					.setItems(items, (dialog, which) -> openDoc(recentUri[which]))
					.setOnDismissListener(dialog -> {
						editor.requestFocus();
						MainActivity.this.dialog = null;
					})
					.show();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	private void SAFOpen() {
//		startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(TYPE_ALL), SAF_OPEN);
		getChooserOpen.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(TYPE_ALL));
	}

	private void SAFSave() {
		this.operation = OPE_EDIT;
//		startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(TYPE_ALL).putExtra(Intent.EXTRA_TITLE, currentFilename), SAF_SAVE);
		getChooserSave.launch(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(TYPE_ALL).putExtra(Intent.EXTRA_TITLE, currentFilename));
	}

	private void SAFSave(int operation, Intent data) {
		this.operation = operation;
		this.operationData = data;
//		startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(TYPE_ALL).putExtra(Intent.EXTRA_TITLE, currentFilename), SAF_SAVE);
		getChooserSave.launch(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(TYPE_ALL).putExtra(Intent.EXTRA_TITLE, currentFilename));
	}

	private void fileWrite() {
		fileWrite(OPE_EDIT, current, null);
	}


	private void fileWrite(int operation, Intent opData) {
		fileWrite(operation, current, opData);
	}

	//写入
	private void fileWrite(int operation, Uri uri, Intent opData) {
		try (ParcelFileDescriptor ofd = Objects.requireNonNull(getContentResolver().openFileDescriptor(uri, MainActivity.KEY_FD_W));
				FileOutputStream os = new FileOutputStream(ofd.getFileDescriptor());
				FileChannel oc = os.getChannel()) {
			String filename;
			if (KEY_SCH_CONTENT.equals(uri.getScheme())) {  //判断协议
				filename = Objects.requireNonNull(DocumentFile.fromSingleUri(this, uri)).getName();
			} else if (KEY_SCH_FILE.equals(uri.getScheme())) {
				filename = uri.getLastPathSegment();
			} else throw new FileNotFoundException();
			Editable e = editor.getText();
			if (e == null) throw new IOException();
			CharSequence s = setLB(e.toString(), lineBreak);    //处理文本并二进制化，写入
			ba2fc(removeZero(Charset.forName(encoding).encode(s.toString()).array()), oc);
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
//		} finally {
//			if (os != null) try {
//				os.close();
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
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
				PrintManager printManager = (PrintManager) MainActivity.this.getSystemService(Context.PRINT_SERVICE);
				PrintDocumentAdapter printAdapter = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? view.createPrintDocumentAdapter(currentFilename) : view.createPrintDocumentAdapter();
				printManager.print(currentFilename, printAdapter, new PrintAttributes.Builder().build());
				printWV.stopLoading();
				printWV = null;
			}
		});
		webView.loadDataWithBaseURL(null, getHTML(), TYPE_HTML, CHARSET_DEFAULT, null);
		printWV = webView;
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
				if (KEY_SCH_CONTENT.equals(u.getScheme())) {
					DocumentFile df = DocumentFile.fromSingleUri(this, u);
					if (df != null && !df.canWrite()) {
						throw new FileNotFoundException();
					}
				} else if (KEY_SCH_FILE.equals(u.getScheme()) && (u.getPath() == null || !new File(u.getPath()).canRead()))
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

	private static byte[] fc2ba(@NonNull FileChannel ic) throws IOException, NonReadableChannelException {
		if (ic.size() > Integer.MAX_VALUE) throw new IOException();
		ByteBuffer buffer = ByteBuffer.allocate((int) ic.size());
		ic.read(buffer);
		return buffer.array();
	}

	private static void ba2fc(byte[] bytes, @NonNull FileChannel oc) throws IOException, NonWritableChannelException {
		oc.write(ByteBuffer.wrap(bytes));
		oc.truncate(bytes.length);
		oc.force(true);
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

	@TargetApi(23)
	private void checkPermission() {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
		}
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
