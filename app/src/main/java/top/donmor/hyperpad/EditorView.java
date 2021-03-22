package top.donmor.hyperpad;

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

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.AppCompatEditText;

import java.util.Objects;

public class EditorView extends AppCompatEditText {

	//常量
	private static final String
			KEY_LOG = ".LOG\n\n",
			LINE_BREAK_LF = "\n",
			STAT_SPLIT = "( |;|\\||\\|\\.|,|:|<|>|'|\"|/|\\?|!|\n|\t|\\{|}|\\(|\\)|\\[|])+";

	EditorCallback editorCallback = null;
	EditHistory currentState;

	private boolean historyOperating, findCaseSensitive = false;
	private int cleanIndex = 0;

	private static final int MAX_STEPS = 100;

	public EditorView(final Context context, AttributeSet attrs) {
		super(context, attrs);
		currentState = new EditHistory(-1, null, null);
		addTextChangedListener(new HistoryListener());
	}

	public boolean isFindCaseSensitive() {
		return findCaseSensitive;
	}

	public void setFindCaseSensitive(boolean findCaseSensitive) {
		this.findCaseSensitive = findCaseSensitive;
	}

	boolean find(String key) {
		if (key.length() == 0) return false;
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
		return true;
	}

	boolean findUp(String key) {
		if (key.length() == 0) return false;
		String text = getEditableText().toString();
		if (!findCaseSensitive) {
			key = key.toLowerCase();
			text = text.toLowerCase();
		}
		int p = text.substring(0, getSelectionStart()).lastIndexOf(key);
		if (p < 0) p = text.lastIndexOf(key);
		if (p < 0) return false;
		setSelection(p, p + key.length());
		return true;
	}

	boolean replace(String key, String content) {
		if (key.length() == 0) return false;
		int start = getSelectionStart(), end = getSelectionEnd();
		Editable text = getEditableText();
		if (key.contentEquals(text.subSequence(start, end))) {
			text.replace(start, end, content);
			setSelection(start + content.length());
		}
		return find(key);
	}

	int replaceAll(String key, String content) {
		if (key.length() == 0) return 0;
		int i = 0;
		setSelection(0);
		if (find(key))
			do {
				i++;
			} while (replace(key, content));
		return i;
	}

	@RequiresApi(api = Build.VERSION_CODES.M)
	@Override
	public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {

		if (event.isCtrlPressed()) {
			switch (keyCode) {
				case KeyEvent.KEYCODE_A:
					return onTextContextMenuItem(android.R.id.selectAll);
				case KeyEvent.KEYCODE_X:
					return onTextContextMenuItem(android.R.id.cut);
				case KeyEvent.KEYCODE_C:
					return onTextContextMenuItem(android.R.id.copy);
				case KeyEvent.KEYCODE_V:
					return onTextContextMenuItem(android.R.id.paste);
				case KeyEvent.KEYCODE_Z:
					if (getCanUndo()) {
						return onTextContextMenuItem(android.R.id.undo);
					}
				case KeyEvent.KEYCODE_Y:
					if (getCanRedo()) {
						return onTextContextMenuItem(android.R.id.redo);
					}
				case KeyEvent.KEYCODE_N:
					editorCallback.newDoc();
					return true;
				case KeyEvent.KEYCODE_O:
					editorCallback.openDoc();
					return true;
				case KeyEvent.KEYCODE_S:
					editorCallback.saveDoc();
					return true;
				case KeyEvent.KEYCODE_P:
					editorCallback.printDoc();
					return true;
				default:
					return super.onKeyDown(keyCode, event);
			}
		} else {
			if (keyCode == KeyEvent.KEYCODE_TAB) {
				int start, end;
				start = Math.max(getSelectionStart(), 0);
				end = Math.max(getSelectionEnd(), 0);
				getEditableText().replace(Math.min(start, end), Math.max(start, end), getResources().getString(R.string.char_tab));
				return true;
			}
			return super.onKeyDown(keyCode, event);
		}
	}

	@Override
	public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
		if (event.isCtrlPressed()) {
			switch (keyCode) {
				case KeyEvent.KEYCODE_A:
				case KeyEvent.KEYCODE_X:
				case KeyEvent.KEYCODE_C:
				case KeyEvent.KEYCODE_V:
				case KeyEvent.KEYCODE_Z:
				case KeyEvent.KEYCODE_Y:
				case KeyEvent.KEYCODE_N:
				case KeyEvent.KEYCODE_O:
				case KeyEvent.KEYCODE_S:
				case KeyEvent.KEYCODE_P:
					return true;
				default:
					return false;
			}
		} else {
			return keyCode == KeyEvent.KEYCODE_TAB;
		}
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
			h.index = currentState.index + 1;
			if (currentState.next != null) purgeHistoryNext(currentState);
			h.past = currentState;
			currentState.next = h;
			currentState = h;
			purgeHistoryPast(currentState, MAX_STEPS);
		}

		public void afterTextChanged(Editable s) {
			editorCallback.setModified(isModified());
			editorCallback.setCanUndo(getCanUndo());
			editorCallback.setCanRedo(getCanRedo());
		}
	}

	private void purgeHistoryNext(EditHistory t) {
		if (t.next != null && t.next.next != null) purgeHistoryNext(t.next);
		t.next = null;
	}

	private void purgeHistoryPast(@NonNull EditHistory t, @IntRange(from = 0) int steps) {
		if (steps > t.index) return;
		if (t.past != null && t.past.past != null) purgeHistoryPast(t.past, steps - 1);
		if (t.past != null && steps <= 0) t.past = null;
	}

	boolean isModified() {
		return cleanIndex != currentState.index;
	}

	void clearModified() {
		cleanIndex = currentState.index;
		editorCallback.setModified(isModified());
	}

	private void resetHistory() {
		purgeHistoryPast(currentState, 0);
		purgeHistoryNext(currentState);
		currentState = new EditHistory(-1, null, null);
		cleanIndex = 0;
		editorCallback.setCanUndo(false);
		editorCallback.setCanRedo(false);
		editorCallback.setModified(false);
	}

	void resetAll() {
		getEditableText().clear();
		resetHistory();
	}

	private boolean getCanUndo() {
		return currentState.past != null;
	}

	private boolean getCanRedo() {
		return currentState.next != null;
	}

	void undo() {
		if (currentState.past == null) return;
		EditHistory past = currentState;
		currentState = currentState.past;
		int start = past.p;
		int end = start + (past.content != null ? past.content.length() : 0);
		Editable text = getEditableText();
		historyOperating = true;
		text.replace(start, end, past.contentBefore);
		historyOperating = false;
		for (Object o : text.getSpans(0,
				text.length(), UnderlineSpan.class)) {
			text.removeSpan(o);
		}
		Selection.setSelection(text, start, past.contentBefore == null ? start : (start + past.contentBefore.length()));
	}

	void redo() {
		if (currentState.next == null) return;
		currentState = currentState.next;
		EditHistory next = currentState;
		int start = next.p;
		int end = start + (next.contentBefore != null ? next.contentBefore.length() : 0);
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

	void loadContent(final CharSequence content) {
		resetHistory();
		historyOperating = true;
		setText(content);
		historyOperating = false;
		setSelection(0);
		requestFocus();
	}

	boolean isLog() {
		return Objects.requireNonNull(getText()).toString().startsWith(KEY_LOG) && (cleanIndex > 0);
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
			while ((k = str.indexOf(LINE_BREAK_LF, i)) >= 0) {
				s[2]++;
				i = k + 1;
			}
		}
		return s;
	}

	static class EditHistory {
		EditHistory next, past = null;
		final CharSequence content;
		final CharSequence contentBefore;
		final int p;
		int index = 0;

		EditHistory(int p, CharSequence content, CharSequence contentBefore) {
			this.p = p;
			this.content = content;
			this.contentBefore = contentBefore;
		}
	}

	interface EditorCallback {
		void newDoc();

		void openDoc();

		void saveDoc();

		void printDoc();

		void setCanUndo(boolean val);

		void setCanRedo(boolean val);

		void setModified(boolean val);

		void selectionChange(int selStart, int selEnd);
	}

	void setEditorCallback(EditorCallback callback) {
		editorCallback = callback;
	}

	@Override
	protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {

	}
}
