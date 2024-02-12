package top.donmor.hyperpad;

import static android.content.Context.CLIPBOARD_SERVICE;

import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Parcelable;
import android.text.Editable;
import android.text.Selection;
import android.text.TextWatcher;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatEditText;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Objects;

public class EditorView extends AppCompatEditText {

	//常量
	private static final String
			KEY_LOG = ".LOG",
			KEY_SDF_TS = "hh:mm yyyy/M/d",
			STAT_SPLIT = "([.\\\\ ;,:<>'\"/?!\t{}()\\[\\]|])+";
	EditorCallback editorCallback = null;
	private final LinkedList<EditHistory> histories;
	private EditHistory cleanState;

	private boolean historyOperating, findCaseSensitive = false;
	private int histIndex = 0;

	private static final int MAX_STEPS = 100;
	private String finding = "";

	public EditorView(final Context context, AttributeSet attrs) {
		super(context, attrs);
		histories = new LinkedList<>();
		cleanState = new EditHistory(-1, null, null);
		histories.add(cleanState);
		addTextChangedListener(new HistoryListener());
	}

	boolean isFindCaseSensitive() {
		return findCaseSensitive;
	}

	void setFindCaseSensitive(boolean findCaseSensitive) {
		this.findCaseSensitive = findCaseSensitive;
	}

	boolean notFound() {
		return !find(finding);
	}

	private boolean find(String key) {
		if (key.length() == 0) {
			requestFocus();
			return false;
		}
		int start = getSelectionStart(), end = getSelectionEnd();
		String text = getEditableText().toString();
		if (!findCaseSensitive) {
			key = key.toLowerCase();
			text = text.toLowerCase();
		}
		int p = text.indexOf(key, key.contentEquals(text.subSequence(start, end)) ? end : start);
		if (p < 0) p = text.indexOf(key);
		if (p < 0) return false;
		setSelection(p, p + key.length());
		requestFocus();
		return true;
	}

	boolean notFoundUp() {
		return !findUp(finding);
	}

	private boolean findUp(String key) {
		if (key.length() == 0) {
			requestFocus();
			return false;
		}
		String text = getEditableText().toString();
		if (!findCaseSensitive) {
			key = key.toLowerCase();
			text = text.toLowerCase();
		}
		int p = text.substring(0, getSelectionStart()).lastIndexOf(key);
		if (p < 0) p = text.lastIndexOf(key);
		if (p < 0) return false;
		setSelection(p, p + key.length());
		requestFocus();
		return true;
	}

	boolean replace(String key, String content) {
		if (key.length() == 0) return false;
		int start = getSelectionStart(), end = getSelectionEnd();
		Editable text = getEditableText();
		if ((findCaseSensitive ? key : key.toLowerCase()).contentEquals((findCaseSensitive ? text : text.toString().toLowerCase()).subSequence(start, end))) {
			text.replace(start, end, content);
			setSelection(start + content.length());
		}
		return find(key);
	}

	int replaceAll(String key, String content) {
		int pos = getSelectionStart();
		boolean wrap = false;
		if (key.length() == 0) return 0;
		int i = 0;
		setSelection(pos);
		if (find(key))
			while (replace(key, content) && !(wrap && pos <= getSelectionStart())) {
				if (pos > getSelectionStart()) {
					wrap = true;
					pos += content.length() - key.length();
				}
				i++;
			}
		return i;
	}

	@Override
	public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
		return onKey(keyCode, event, super.onKeyDown(keyCode, event));
	}

	boolean onKey(int keyCode, @NonNull KeyEvent event, boolean fallback) {
		boolean canCp = getSelectionStart() != getSelectionEnd();
		if (!event.isCtrlPressed() && event.isAltPressed() && !event.isShiftPressed() && !event.isMetaPressed() && !event.isFunctionPressed() && !event.isSymPressed()) {
			switch (keyCode) {
				case KeyEvent.KEYCODE_F:
					editorCallback.invokeMenu(R.id.action_file);
					return true;
				case KeyEvent.KEYCODE_E:
					editorCallback.invokeMenu(R.id.action_edit);
					return true;
				case KeyEvent.KEYCODE_V:
					editorCallback.invokeMenu(R.id.action_view);
					return true;
			}
		} else if (event.isCtrlPressed() && !event.isAltPressed() && !event.isShiftPressed() && !event.isMetaPressed() && !event.isFunctionPressed() && !event.isSymPressed()) {
			switch (keyCode) {
				case KeyEvent.KEYCODE_N:    //新建
					editorCallback.newDoc();
					return true;
				case KeyEvent.KEYCODE_O:    //打开
					editorCallback.openDoc();
					return true;
				case KeyEvent.KEYCODE_S:    //保存
					editorCallback.save(false);
					return true;
				case KeyEvent.KEYCODE_P:    //打印
					editorCallback.printDoc();
					return true;
				case KeyEvent.KEYCODE_Q:    //退出
					editorCallback.quit();
					return true;
				case KeyEvent.KEYCODE_Z:    //撤销
					if (canUndo())
						return onTextContextMenuItem(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? android.R.id.undo : 0);
					break;
				case KeyEvent.KEYCODE_Y:    //重做
					if (canRedo())
						return onTextContextMenuItem(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? android.R.id.redo : 0);
					break;
				case KeyEvent.KEYCODE_X:    //剪切
					if (canCp) return onTextContextMenuItem(android.R.id.cut);
					break;
				case KeyEvent.KEYCODE_C:    //复制
					if (canCp) return onTextContextMenuItem(android.R.id.copy);
					break;
				case KeyEvent.KEYCODE_V:    //粘贴
					if (((ClipboardManager) getContext().getSystemService(CLIPBOARD_SERVICE)).hasPrimaryClip())
						return onTextContextMenuItem(android.R.id.paste);
					break;
				case KeyEvent.KEYCODE_A:    //全选
					return onTextContextMenuItem(android.R.id.selectAll);
				case KeyEvent.KEYCODE_F:    //查找
					editorCallback.find(false);
					return true;
				case KeyEvent.KEYCODE_H:    //替换
					editorCallback.find(true);
					return true;
				case KeyEvent.KEYCODE_G:    //转到
					editorCallback.gotoDialog();
					return true;
			}
		} else if (event.isCtrlPressed() && !event.isAltPressed() && event.isShiftPressed() && !event.isMetaPressed() && !event.isFunctionPressed() && !event.isSymPressed()) {
			if (keyCode == KeyEvent.KEYCODE_S) {    //另存为
				editorCallback.save(true);
				return true;
			}
		} else if (!event.isCtrlPressed() && !event.isAltPressed() && event.isShiftPressed() && !event.isMetaPressed() && !event.isFunctionPressed() && !event.isSymPressed()) {
			if (keyCode == KeyEvent.KEYCODE_F3) {    //查找上一个
				if (notFoundUp())
					Toast.makeText(getContext(), R.string.find_not_found, Toast.LENGTH_SHORT).show();
				return true;
			}
		} else if (!event.isCtrlPressed() && !event.isAltPressed() && !event.isShiftPressed() && !event.isMetaPressed() && !event.isFunctionPressed() && !event.isSymPressed()) {
			switch (keyCode) {
				case KeyEvent.KEYCODE_TAB:    //插入制表符 (4 Spaces)
					int start, end;
					start = Math.max(getSelectionStart(), 0);
					end = Math.max(getSelectionEnd(), 0);
					getEditableText().replace(Math.min(start, end), Math.max(start, end), getResources().getString(R.string.char_tab));
					return true;
				case KeyEvent.KEYCODE_F5: //时间日期
					insertTimestamp();
					return true;
				case KeyEvent.KEYCODE_F3: //查找下一个
					if (notFound())
						Toast.makeText(getContext(), R.string.find_not_found, Toast.LENGTH_SHORT).show();
					return true;
			}
		}
		return fallback;
	}

	@Override
	public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
		if (!event.isCtrlPressed() && event.isAltPressed() && !event.isShiftPressed() && !event.isMetaPressed() && !event.isFunctionPressed() && !event.isSymPressed()) {
			return keyCode == KeyEvent.KEYCODE_F || keyCode == KeyEvent.KEYCODE_E || keyCode == KeyEvent.KEYCODE_V;
		} else if (event.isCtrlPressed() && !event.isAltPressed() && !event.isShiftPressed() && !event.isMetaPressed() && !event.isFunctionPressed() && !event.isSymPressed()) {
			return keyCode == KeyEvent.KEYCODE_N || keyCode == KeyEvent.KEYCODE_O || keyCode == KeyEvent.KEYCODE_S || keyCode == KeyEvent.KEYCODE_P || keyCode == KeyEvent.KEYCODE_Q || keyCode == KeyEvent.KEYCODE_Z || keyCode == KeyEvent.KEYCODE_Y || keyCode == KeyEvent.KEYCODE_X || keyCode == KeyEvent.KEYCODE_C || keyCode == KeyEvent.KEYCODE_V || keyCode == KeyEvent.KEYCODE_A || keyCode == KeyEvent.KEYCODE_F || keyCode == KeyEvent.KEYCODE_H || keyCode == KeyEvent.KEYCODE_G;
		} else if (event.isCtrlPressed() && !event.isAltPressed() && event.isShiftPressed() && !event.isMetaPressed() && !event.isFunctionPressed() && !event.isSymPressed()) {
			return keyCode == KeyEvent.KEYCODE_S;
		} else if (!event.isCtrlPressed() && !event.isAltPressed() && event.isShiftPressed() && !event.isMetaPressed() && !event.isFunctionPressed() && !event.isSymPressed()) {
			return keyCode == KeyEvent.KEYCODE_F3;
		} else if (!event.isCtrlPressed() && !event.isAltPressed() && !event.isShiftPressed() && !event.isMetaPressed() && !event.isFunctionPressed() && !event.isSymPressed()) {
			return keyCode == KeyEvent.KEYCODE_TAB || keyCode == KeyEvent.KEYCODE_F5 || keyCode == KeyEvent.KEYCODE_F3;
		}
		return false;
	}

	@Override
	protected void onSelectionChanged(int selStart, int selEnd) {
		super.onSelectionChanged(selStart, selEnd);
		if (editorCallback != null) editorCallback.selectionChange(selStart, selEnd);
	}

	@Override
	public boolean onTextContextMenuItem(final int id) {
		if (id == android.R.id.undo) {
			undo();
			return true;
		} else if (id == android.R.id.redo) {
			redo();
			return true;
		} else {
			return super.onTextContextMenuItem(id);
		}
	}

	void setFinding(String str) {
		finding = str;
	}

	String getFinding() {
		return finding;
	}

	private final class HistoryListener implements TextWatcher {

		CharSequence beforeChange, afterChange;

		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			if (historyOperating) {
				return;
			}

			beforeChange = s.subSequence(start, start + count);
		}

		public void onTextChanged(CharSequence s, int start, int before, int count) {
			if (historyOperating) {
				return;
			}
			afterChange = s.subSequence(start, start + count);
			EditHistory h = new EditHistory(start, afterChange, beforeChange);
			while (histIndex < histories.size() - 1) histories.removeLast();
			histories.add(h);
			while (histories.size() > MAX_STEPS + 1) histories.removeFirst();
			histIndex = histories.indexOf(h);
		}

		public void afterTextChanged(Editable s) {
			editorCallback.setModified(isModified());
			editorCallback.setCanUndo(canUndo());
			editorCallback.setCanRedo(canRedo());
		}
	}

	boolean isModified() {
		return cleanState == null || !histories.isEmpty() && cleanState != histories.get(histIndex);
	}

	void clearModified() {
		cleanState = histories.get(histIndex);
		editorCallback.setModified(isModified());
	}

	private void resetHistory() {
		histories.clear();
		cleanState = new EditHistory(-1, null, null);
		histories.add(cleanState);
		histIndex = 0;
		editorCallback.setCanUndo(false);
		editorCallback.setCanRedo(false);
		editorCallback.setModified(false);
	}

	void resetAll() {
		getEditableText().clear();
		resetHistory();
	}

	private boolean canUndo() {
		return !histories.isEmpty() && histIndex > 0;
	}

	private boolean canRedo() {
		return histIndex < histories.size() - 1;
	}

	void undo() {
		if (!canUndo()) return;
		EditHistory past = histories.get(histIndex);
		histIndex -= 1;
		int start = past.p, end = start + (past.content != null ? past.content.length() : 0);
		Editable text = getEditableText();
		historyOperating = true;
		text.replace(start, end, past.contentBefore);
		historyOperating = false;
		for (Object o : text.getSpans(0, text.length(), UnderlineSpan.class)) {
			text.removeSpan(o);
		}
		Selection.setSelection(text, start, past.contentBefore == null ? start : (start + past.contentBefore.length()));
	}

	void redo() {
		if (!canRedo()) return;
		histIndex += 1;
		EditHistory next = histories.get(histIndex);
		int start = next.p, end = start + (next.contentBefore != null ? next.contentBefore.length() : 0);
		Editable text = getEditableText();
		historyOperating = true;
		text.replace(start, end, next.content);
		historyOperating = false;
		for (Object o : text.getSpans(0,
				text.length(), UnderlineSpan.class)) {
			text.removeSpan(o);
		}
		Selection.setSelection(text, start, next.content == null ? start : (start + next.content.length()));
	}

	void setDirty() {
		cleanState = null;
	}

	//时间戳
	void insertTimestamp() {
		getEditableText().replace(getSelectionStart(), getSelectionEnd(), getTimestamp());
	}

	private String getTimestamp() {
		return new SimpleDateFormat(KEY_SDF_TS, Locale.ENGLISH).format(new Date(System.currentTimeMillis()));
	}

	void loadContent(final CharSequence content) {
		resetHistory();
		historyOperating = true;
		setText(content);
		historyOperating = false;
		if (Objects.requireNonNull(getText()).toString().startsWith(KEY_LOG))
			append(new StringBuilder(MainActivity.LINE_BREAK.LF.s).append(getTimestamp()).append(MainActivity.LINE_BREAK.LF.s));
		setSelection(0);
		requestFocus();
	}

	int[] statistics() {
		int[] s = new int[3];
		Editable e = getText();
		if (e != null) {
			String str = e.toString();
			s[0] = str.length();
			s[1] = s[0] > 0 ? str.split(STAT_SPLIT).length : 0;
			s[2] = 1;
			int i = 0, k;
			while ((k = str.indexOf(MainActivity.LINE_BREAK.LF.s, i)) >= 0) {
				s[2]++;
				i = k + 1;
			}
		}
		return s;
	}

	static class EditHistory {
		final CharSequence content;
		final CharSequence contentBefore;
		final int p;

		EditHistory(int p, CharSequence content, CharSequence contentBefore) {
			this.p = p;
			this.content = content;
			this.contentBefore = contentBefore;
		}
	}

	//接口对主界面操作
	interface EditorCallback {
		//Alt打开菜单
		void invokeMenu(int id);

		//新建
		void newDoc();

		//打开
		void openDoc();

		//保存另存
		void save(boolean a);

		//打印
		void printDoc();

		//退出
		void quit();

		//查找替换
		void find(boolean replace);

		//转到
		void gotoDialog();

		//菜单能否撤销
		void setCanUndo(boolean val);

		//菜单能否重做
		void setCanRedo(boolean val);

		//更新修改状态
		void setModified(boolean val);

		//更新选区
		void selectionChange(int selStart, int selEnd);
	}

	void setEditorCallback(EditorCallback callback) {
		editorCallback = callback;
	}

	@Override
	protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {

	}
}