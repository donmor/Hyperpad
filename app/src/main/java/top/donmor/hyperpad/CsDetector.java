//package top.donmor.hyperpad;
//
//import org.mozilla.intl.chardet.nsDetector;
//import org.mozilla.intl.chardet.nsICharsetDetectionObserver;
//
//import java.io.BufferedInputStream;
//import java.io.ByteArrayInputStream;
//import java.io.IOException;
//
//final class CsDetector {
//	private nsICharsetDetectionObserver observer;
//	private nsDetector detector;
//
//	CsDetector() {
//		observer = new nsICharsetDetectionObserver() {
//			@Override
//			public void Notify(String s) {
//				;
//			}
//		};
//		detector = new nsDetector();
////		detector.Init(observer);
//	}
//
//	String[] getProbCharsets() {
//		return detector.getProbableCharsets();
//	}
//
//	String detect(byte[] data) {
//		detector.Init(observer);
//		if (detectAscii(data)) return "ASCII";
//		String[] prob = getProbCharsets();
//		if (prob.length > 0) return prob[0];
//		else return null;
//	}
//
//	boolean detectAscii(byte[] data) {
//		BufferedInputStream input = null;
//		boolean done = false, isAscii = true;
//		try {
//			input = new BufferedInputStream(new ByteArrayInputStream(data), 4096);
//			byte[] b = new byte[4096];
//			int hasRead;
//			while ((hasRead = input.read(b)) != -1) {
//				if (isAscii) isAscii = detector.isAscii(b, hasRead);
//				if (!isAscii && !done) done = detector.DoIt(b, hasRead, false);
//			}
////			return isAscii;
//		} catch (IOException e) {
//			e.printStackTrace();
//		} finally {
//			detector.DataEnd();
//			if (input != null) {
//				try {
//					input.close();
//				} catch (IOException e) {
//					e.printStackTrace();
//				}
//			}
//
//		}
//		return isAscii;
//	}
//}
