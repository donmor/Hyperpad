package top.donmor.hyperpad;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

	static {
		AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
	}

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
			KEY_MODIFIED = "* ",
			LINE_COL_KEY = "(",
			LINE_COL_KEY2 = ", ",
			LINE_COL_KEY3 = ")",
			LINE_COL_KEY4 = " ~ (",
			LINE_BREAK_LF = "\n",
			LINE_BREAK_CR = "\r",
			LINE_BREAK_CRLF = "\r\n",
			TYPE_ALL = "*/*";
	private static final int
			BUF_SIZE = 8192,
			OPE_EDIT = 0,
			OPE_NEW = 1,
			OPE_OPEN = 2,
			OPE_RECENT = 3,
			OPE_CLOSE = 4,
			OPE_ACTION = 5,
			SAF_OPEN = 42,
			SAF_SAVE = 43,
			TAKE_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

	private final String[] charsets = Charset.availableCharsets().keySet().toArray(new String[0]);

	private AppBarLayout appbar;
	private Toolbar toolbar;
	private EditorView editor;
	private SharedPreferences preferences;
	private AlertDialog dialog;

	private boolean canUndo = false, canRedo = false, statusBar = false, wrap = false, mono = false;
	private int fontSize = 18, dialogPadding = 30, operation = OPE_EDIT;
	private String lineBreak = LINE_BREAK_LF, encoding = CHARSET_DEFAULT, currentFilename;

	private Uri current = null;
	private Uri[] recentUri = new Uri[0];

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
		setContentView(R.layout.activity_main);

		dialogPadding = (int) (getResources().getDisplayMetrics().density * 20);
		currentFilename = getString(android.R.string.untitled);

		preferences = getSharedPreferences(KEY_CFG_FILE, MODE_PRIVATE);
		statusBar = preferences.getBoolean(KEY_CFG_STAT, false);
		wrap = preferences.getBoolean(KEY_CFG_WRAP, true);
		mono = preferences.getBoolean(KEY_CFG_MONO, false);
		fontSize = preferences.getInt(KEY_CFG_SIZE, 18);
		String[] rSet = preferences.getStringSet(KEY_CFG_RECENT, new HashSet<String>()).toArray(new String[0]);
		recentUri = new Uri[rSet.length];
		for (int i = 0; i < rSet.length; i++) recentUri[i] = Uri.parse(rSet[i]);
		appbar = findViewById(R.id.appbar);
		toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		this.setTitle(R.string.app_name);
		toolbar.setSubtitle(currentFilename);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			getWindow().setStatusBarColor(getColor(R.color.design_default_color_primary));
			getWindow().getDecorView().setSystemUiVisibility((getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : View.SYSTEM_UI_FLAG_VISIBLE);
		}
		editor = findViewById(R.id.editor_view);
		editor.setHorizontallyScrolling(!wrap);
		editor.setTypeface(mono ? Typeface.MONOSPACE : Typeface.DEFAULT);
		editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
		editor.setEditorCallback(new EditorView.EditorCallback() {
			@Override
			public void save() {
				if (current == null) SAFSave(OPE_EDIT);
				else fileWrite(OPE_EDIT);
			}

			@Override
			public void setCanUndo(boolean val) {
				canUndo = val;
			}

			@Override
			public void setCanRedo(boolean val) {
				canRedo = val;
			}

			@Override
			public void setModified(boolean val) {
				if (val) toolbar.setSubtitle(KEY_MODIFIED + currentFilename);
				else toolbar.setSubtitle(currentFilename);
			}

			@Override
			public void selectionChange(int selStart, int selEnd) {
				LinearLayout statBar = appbar.findViewById(R.id.status_bar_widget);
				if (statBar == null) return;
				TextView lineCol = statBar.findViewById(R.id.status_bar_line_col);
				Editable editable = editor.getText();
				if (editable != null) {
					String ws = editable.toString().substring(0, selStart) + LINE_COL_KEY;
					String[] lines = ws.split(LINE_BREAK_LF);
					int l = lines.length, c = lines[l - 1].length();
					lineCol.setText(LINE_COL_KEY);
					lineCol.append(String.valueOf(l));
					lineCol.append(LINE_COL_KEY2);
					lineCol.append(String.valueOf(c));
					lineCol.append(LINE_COL_KEY3);
					if (selEnd > selStart) {
						ws = editable.toString().substring(0, selEnd) + LINE_COL_KEY;
						lines = ws.split(LINE_BREAK_LF);
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
		if (
				Intent.ACTION_VIEW.
						equals(
								getIntent().
										getAction()))
			if (editor.isModified()) confirm(OPE_ACTION);
			else openAction();
		else
			System.out.println();

	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		System.out.println(Intent.ACTION_VIEW);
		System.out.println(intent.getAction());
		if (
				Intent.ACTION_VIEW.
						equals(
								intent.
										getAction())) {
			setIntent(intent);
			if (editor.isModified()) confirm(OPE_ACTION);
			else openAction();
		} else
			System.out.println();
	}

	private void openAction() {
		Uri uri = getIntent().getData();
		if (uri != null) openDoc(uri);

	}

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

	@SuppressLint("InflateParams")
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_new:
				if (editor.isModified()) {
					confirm(OPE_NEW);
				} else newDoc();
				break;
			case R.id.action_open:
				if (editor.isModified()) confirm(OPE_OPEN);
				else SAFOpen();
				break;
			case R.id.action_recent:
				if (editor.isModified()) confirm(OPE_RECENT);
				else openRecent();
				break;
			case R.id.action_save:
				if (current == null) SAFSave(OPE_EDIT);
				else fileWrite(OPE_EDIT);
				break;
			case R.id.action_save_as:
				SAFSave(OPE_EDIT);
				break;
			case R.id.action_about:
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
			case R.id.action_close:
				onBackPressed();
				break;
			case R.id.action_undo:
				editor.undo();
				break;
			case R.id.action_redo:
				editor.redo();
				break;
			case R.id.action_cut:
				editor.onTextContextMenuItem(android.R.id.cut);
				break;
			case R.id.action_copy:
				editor.onTextContextMenuItem(android.R.id.copy);
				break;
			case R.id.action_paste:
				editor.onTextContextMenuItem(android.R.id.paste);
				break;
			case R.id.action_select_all:
				editor.onTextContextMenuItem(android.R.id.selectAll);
				break;
			case R.id.action_find:
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
			case R.id.action_goto:
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
									int v = content.indexOf(LINE_BREAK_LF, p);
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
			case R.id.action_line_break:
				//noinspection StringEquality
				final int v = lineBreak == LINE_BREAK_CRLF ? 1 : lineBreak == LINE_BREAK_CR ? 2 : 0;
				if (dialog != null) dialog.dismiss();
				dialog = new AlertDialog.Builder(this)
						.setTitle(R.string.action_line_break)
						.setSingleChoiceItems(R.array.line_breaks, v, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								if (which == v) return;
								lineBreak = which == 1 ? LINE_BREAK_CRLF : which == 2 ? LINE_BREAK_CR : LINE_BREAK_LF;
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
			case R.id.action_encoding:
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
			case R.id.action_status_bar:
				statusBar = !item.isChecked();
				item.setChecked(statusBar);
				if (statusBar) loadStatusBar();
				else appbar.removeView(appbar.findViewById(R.id.status_bar_widget));
				preferences.edit().putBoolean(KEY_CFG_STAT, statusBar).apply();
				break;
			case R.id.action_wrap_text:
				wrap = !item.isChecked();
				item.setChecked(wrap);
				editor.setHorizontallyScrolling(!wrap);
				preferences.edit().putBoolean(KEY_CFG_WRAP, wrap).apply();
				break;
			case R.id.action_font_monospace:
				mono = !item.isChecked();
				item.setChecked(mono);
				editor.setTypeface(mono ? Typeface.MONOSPACE : Typeface.DEFAULT);
				preferences.edit().putBoolean(KEY_CFG_MONO, mono).apply();
				break;
			case R.id.action_font_size:
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
			case R.id.action_statistics:
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
						fileWrite(this.operation, uri);
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
		//noinspection StringEquality
		lb.setText(lineBreak == LINE_BREAK_LF ? R.string.status_lb_lf : lineBreak == LINE_BREAK_CRLF ? R.string.status_lb_crlf : R.string.status_lb_cr);
		enc.setText(encoding);

	}

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
			//noinspection StringEquality
			if (!preferences.edit()
					.putBoolean(KEY_SIS, true)
					.putBoolean(KEY_SIS_CHANGE, editor.isModified())
					.putString(KEY_SIS_ID, id)
					.putInt(KEY_SIS_LEN, editor.length())
					.putString(KEY_SIS_CURRENT, current != null ? current.toString() : null)
					.putString(KEY_SIS_CURRENT_NAME, currentFilename)
					.putInt(KEY_SIS_LB, lineBreak == LINE_BREAK_CRLF ? 1 : lineBreak == LINE_BREAK_CR ? 2 : 0)
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
				lineBreak = e == 1 ? LINE_BREAK_CRLF : e == 2 ? LINE_BREAK_CR : LINE_BREAK_LF;
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
		if (operation == OPE_EDIT) return;
		if (dialog != null) dialog.dismiss();
		dialog = new AlertDialog.Builder(this)
				.setTitle(android.R.string.dialog_alert_title)
				.setMessage(R.string.file_save_confirm)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (current == null) SAFSave(operation);
						else fileWrite(operation);
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.setNeutralButton(R.string.do_not_save, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						processOperation(operation);
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

	private void newDoc() {
		if (!editor.isModified() && current != null && editor.isLog())
			fileLog();
		setRecent(current);
		current = null;
		currentFilename = getString(android.R.string.untitled);
		editor.resetAll();
		lineBreak = LINE_BREAK_LF;
		encoding = CHARSET_DEFAULT;
		updateStatusBar();
		editor.setSelection(0);
		editor.requestFocus();
	}

	@SuppressWarnings("UnusedAssignment")
	private void openDoc(Uri uri) {
		BufferedInputStream is = null;
		ByteArrayOutputStream os = null;
		boolean retry = false;
		try {
			is = new BufferedInputStream(Objects.requireNonNull(getContentResolver().openInputStream(uri)));
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
			b = os.toByteArray();
			is.close();
			os.close();
			CharsetDetector detector = new CharsetDetector();
			CharsetMatch[] matches = detector.setText(b).detectAll();
			if (matches == null || matches.length == 0) throw new IOException();
			CharsetMatch match = matches[0];
			if (match.getConfidence() <= 10) retry = true;
			if (!editor.isModified() && current != null && editor.isLog())
				fileLog();
			setRecent(current);
			current = uri;
			currentFilename = Objects.requireNonNull(DocumentFile.fromSingleUri(this, uri)).getName();
			encoding = match.getName();
			String content = match.getString();
			if (!retry) {
				detector = null;
				match = null;
				b = null;
			}
			lineBreak = content.contains(LINE_BREAK_CRLF) ? LINE_BREAK_CRLF : content.contains(LINE_BREAK_CR) ? LINE_BREAK_CR : LINE_BREAK_LF;
			CharSequence sequence = trimLB(content);
			content = null;
			editor.loadContent(sequence);
			updateStatusBar();
			try {
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
			for (int i = 0; i < items.length; i++)
				items[i] = URLDecoder.decode(recentUri[i].getLastPathSegment(), CHARSET_DEFAULT);
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

	private void SAFSave(int operation) {
		this.operation = operation;
		startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(TYPE_ALL).putExtra(Intent.EXTRA_TITLE, currentFilename), SAF_SAVE);
	}

	private void fileWrite(int operation) {
		fileWrite(operation, current);
	}

	private void fileLog() {
		fileWrite(operation, current, true);
	}

	private void fileWrite(int operation, Uri uri) {
		fileWrite(operation, uri, false);
	}

	@SuppressWarnings("UnusedAssignment")
	private void fileWrite(int operation, Uri uri, boolean log) {
		BufferedInputStream is = null;
		BufferedOutputStream os = null;
		try {
			os = new BufferedOutputStream(Objects.requireNonNull(getContentResolver().openOutputStream(uri)));
			Editable e = editor.getText();
			if (e == null) throw new IOException();
			String s = setLB(e.toString(), lineBreak);
			if (log || operation == OPE_NEW || operation == OPE_CLOSE)
				s += new SimpleDateFormat(KEY_LOG_SDF, Locale.ENGLISH).format(new Date(System.currentTimeMillis()));
			is = new BufferedInputStream(new ByteArrayInputStream(removeZero(Charset.forName(encoding).encode(s).array())));
			s = null;
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
			currentFilename = Objects.requireNonNull(DocumentFile.fromSingleUri(this, uri)).getName();
			editor.clearModified();
			try {
				getContentResolver().takePersistableUriPermission(uri, TAKE_FLAGS);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			processOperation(operation);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			SAFSave(operation);
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

	private void processOperation(int operation) {
		this.operation = OPE_EDIT;
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
				if (!editor.isModified() && current != null && editor.isLog())
					fileLog();
				setRecent(current);
				super.onBackPressed();
				break;
			case OPE_ACTION:
				openAction();
				break;
		}
	}

	private CharSequence trimLB(String c) {
		StringBuilder builder = new StringBuilder(c);
		int index;
		while (true) {
			index = builder.indexOf(LINE_BREAK_CRLF);
			if (index < 0) break;
			builder.replace(index, index + 2, LINE_BREAK_LF);
		}
		while (true) {
			index = builder.indexOf(LINE_BREAK_CR);
			if (index < 0) break;
			builder.replace(index, index + 1, LINE_BREAK_LF);
		}
		return builder;
	}

	private String setLB(String c, String lineBreak) {
		//noinspection StringEquality
		if (lineBreak == LINE_BREAK_LF) return c;
		StringBuilder builder = new StringBuilder(c);
		int index, p = 0;
		while (true) {
			index = builder.indexOf(LINE_BREAK_LF, p);
			if (index < 0) break;
			builder.replace(index, index + 1, lineBreak);
			p = index + lineBreak.length();
		}
		return builder.toString();
	}

	private void setRecent() {
		setRecent(null);
	}

	private void setRecent(Uri uri) {
		int count = 0;
		Uri[] ux = new Uri[recentUri.length];
		for (Uri u : recentUri) {
			try {
				getContentResolver().openInputStream(u);
				if (!u.equals(uri)) ux[count++] = u;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		int w = uri != null ? 1 : 0;
		int s = Math.min(10, count + w);
		Uri[] uris = new Uri[s];
		if (w > 0) uris[w - 1] = uri;
		System.arraycopy(ux, 0, uris, w, s - w);
		recentUri = uris;
		String[] x = new String[recentUri.length];
		for (int i = 0; i < recentUri.length; i++) x[i] = recentUri[i].toString();
		preferences.edit().putStringSet(KEY_CFG_RECENT, new HashSet<>(Arrays.asList(x))).apply();
	}

	private static byte[] removeZero(byte[] b) {
		final byte zero = 0x00;
		while (b[b.length - 1] == zero) {
			byte[] c = new byte[b.length - 1];
			System.arraycopy(b, 0, c, 0, c.length);
			b = c;
		}
		return b;
	}
}
