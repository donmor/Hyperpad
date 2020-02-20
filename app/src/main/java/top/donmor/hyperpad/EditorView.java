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
import android.view.AbsSavedState;
import android.view.KeyEvent;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.AppCompatEditText;

import java.util.Objects;

public class EditorView extends AppCompatEditText {

	public static final String KEY_LOG = ".LOG\n\n";

	EditorCallback editorCallback = null;
	HistoryListener historyListener;
	EditHistory currentState;
	Context context;

	boolean historyOperating;
	int cleanIndex = 0,
			maxSteps = 100;

	public EditorView(final Context context, AttributeSet attrs) {
		super(context, attrs);
		this.context = context;
		historyListener = new HistoryListener();
		currentState = new EditHistory(-1, null, null);
		addTextChangedListener(historyListener);
	}

	boolean find(String key) {
		if (key.length() == 0) return false;
		int start = getSelectionStart(), end = getSelectionEnd();
		Editable text = getEditableText();
		int p = text.toString().indexOf(key, key.contentEquals(text.subSequence(start, end)) ? end : start);
		if (p < 0) p = text.toString().indexOf(key);
		if (p < 0) return false;
		setSelection(p, p + key.length());
		return true;
	}

	boolean findUp(String key) {
		if (key.length() == 0) return false;
		Editable text = getEditableText();
		int p = text.toString().substring(0, getSelectionStart()).lastIndexOf(key);
		if (p < 0) p = text.toString().lastIndexOf(key);
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
			return find(key);
		} else return find(key);
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
				case KeyEvent.KEYCODE_S:
					return editorCallback.save();
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
				case KeyEvent.KEYCODE_S:
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
			purgeHistoryPast(currentState, maxSteps);
		}

		public void afterTextChanged(Editable s) {
			editorCallback.setModified(isModified());
			editorCallback.setCanUndo(getCanUndo());
			editorCallback.setCanRedo(getCanRedo());
		}
	}

	void purgeHistoryNext(EditHistory t) {
		if (t.next != null && t.next.next != null) purgeHistoryNext(t.next);
		t.next = null;
	}

	void purgeHistoryPast(@NonNull EditHistory t, @IntRange(from = 0) int steps) {
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

	void resetHistory(boolean newDoc) {
		purgeHistoryPast(currentState, 0);
		purgeHistoryNext(currentState);
		currentState = new EditHistory(-1, null, null);
		if (!newDoc) currentState.index = 1;
		cleanIndex = 0;
		editorCallback.setCanUndo(false);
		editorCallback.setCanRedo(false);
		editorCallback.setModified(false);
	}

	void resetAll() {
		getEditableText().clear();
		resetHistory(true);
	}

	boolean getCanUndo() {
		return currentState.past != null;
	}

	boolean getCanRedo() {
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
		resetHistory(true);
//		final ProgressDialog dialog = new ProgressDialog(context);
//		dialog.setMessage("");
//		dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
//		dialog.setCanceledOnTouchOutside(false);
//		final Thread thread = new Thread(new Runnable() {
//			@Override
//			public void run() {
//		dialog.show();
		historyOperating = true;
//		new OpenSync(this);
//
//		int buf = MainActivity.BUF_SIZE;
//		if (buf >= content.length()) setText(content);
//		else {
//			int r = content.length() / buf, d = content.length() % buf;
//			setText(content.subSequence(0, buf));
//			for (int i = 1; i < r; i++) {
//				append(content.subSequence(i * buf, (i + 1) * buf));
//			}
//			append(content.subSequence(r * buf, r * buf + d));
//		}
		setText(content);
		historyOperating = false;
//		dialog.dismiss();
		setSelection(0);
		requestFocus();
//			}
//		});
//		dialog.setButton(DialogInterface.BUTTON_NEGATIVE, context.getText(android.R.string.cancel), new DialogInterface.OnClickListener() {
//			@Override
//			public void onClick(DialogInterface dialogInterface, int i) {
//				dialog.cancel();
//			}
//		});
//		dialog.setOnShowListener(new DialogInterface.OnShowListener() {
//			@Override
//			public void onShow(DialogInterface dialog) {
//				thread.start();
//			}
//		});
//		dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
//			@Override
//			public void onCancel(DialogInterface dialogInterface) {
//				thread.interrupt();
//			}
//		});
//		dialog.show();
	}

	boolean isLog() {
		return Objects.requireNonNull(getText()).toString().startsWith(KEY_LOG);
	}

//	private static class OpenSync extends AsyncTask<CharSequence, Integer, String> {
//
//		private final WeakReference<EditorView> parent;
//
//		OpenSync(EditorView view) {
//			this.parent = new WeakReference<>(view);
//		}
//
//		@Override
//		protected String doInBackground(CharSequence... params) {
//			runOnU
//			parent.get().setText(params[0]);
//			return null;
//		}
//	}

//	void loadContent(final byte[] content, String encoding) {
//		resetHistory();
//		historyOperating = true;
//
//		text.clear();
//
//		BufferedReader reader = null;
//		try {
//			reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(content), encoding));
//			int length, lenTotal = 0;
//
//			char[] v = new char[MainActivity.BUF_SIZE];
//			while ((length = reader.read(v, lenTotal, MainActivity.BUF_SIZE)) != -1) {
//				lenTotal+=length;
//				StringBuilder builder =
//				append(v,0,length);
//			}
//		} catch (Exception e) {
//			e.printStackTrace();
//		} finally {
//			if (reader != null) try {
//				reader.close();
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
//		}
//
//		historyOperating = false;
//		setSelection(0);
//		requestFocus();
//	}

	class EditHistory {
		EditHistory next, past = null;
		CharSequence content, contentBefore;
		int p;
		int index = 0;

		EditHistory(int p, CharSequence content, CharSequence contentBefore) {
			this.p = p;
			this.content = content;
			this.contentBefore = contentBefore;
		}
	}

	interface EditorCallback {
		boolean save();

		void setCanUndo(boolean val);

		void setCanRedo(boolean val);

		void setModified(boolean val);

		void selectionChange(int selStart, int selEnd);
	}

	void setEditorCallback(EditorCallback callback) {
		editorCallback = callback;
	}

	protected Parcelable onSaveInstantState() {
		return AbsSavedState.EMPTY_STATE;
	}

//	@Override
//	public void saveHierarchyState(SparseArray<Parcelable> container) {
//
//	}

	@Override
	protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {

	}
}
