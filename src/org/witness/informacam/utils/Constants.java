package org.witness.informacam.utils;

import java.util.Collections;
import java.util.List;
import java.util.Vector;

import android.net.Uri;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v4.app.FragmentManager;

public class Constants {

	public interface ModelListener {
		public void requestUpdate();
	}

	public interface InformaCamEventListener {
		public void onUpdate(Message message);
	}
	
	public interface WizardListener {
		public FragmentManager returnFragmentManager();
		public void wizardCompleted();
		public void onSubFragmentCompleted();
	}

	public final static class Actions {
		public final static String INIT = "org.witness.informacam.action.INIT";
		public final static String SHUTDOWN = "org.witness.informacam.action.SHUTDOWN";
		public final static String ASSOCIATE_SERVICE = "org.witness.informacam.action.ASSOCIATE_SERVICE";
		public static final String UPLOADER_UPDATE = "org.witness.informacam.action.UPLOADER_UPDATE";
		public static final String CAMERA = "android.media.action.IMAGE_CAPTURE";
	}

	public final static class Codes {
		public final static class Routes {
			public final static int IMAGE_CAPTURE = 100;
			public final static int SIGNATURE_SERVICE = 101;
			public static final int IO_SERVICE = 102;
			public static final int UPLOADER_SERVICE = 103;
			public static final int RETRY_SAVE = 104;
			public static final int RETRY_GET = 105;
		}

		public final static class Keys {
			public final static String SERVICE = "service";
			public static final String IV = "iv";
			public static final String VALUE = "value";
			public static final String UPLOADER = "uploader";
			public static final String DCIM_DESCRIPTOR = "dcimDescriptor";

			public static final class UI {
				public static final String PROGRESS = "progress";
				public static final String UPDATE = "update";
			}
		}
		
		public final static class Extras {
			public final static String WIZARD_SUPPLEMENT = "wizard_supplement";
			public static final String MESSAGE_CODE = "message_code";
			public static final String RETURNED_MEDIA = "informacam_returned_media";
		}

		public static final class Messages {

			public static final class Wizard {
				public final static int INIT = 300;
			}
			
			public static final class UI {
				public final static int UPDATE = 301;
				public static final int REPLACE = 302;
			}
			
			public static final class Login {
				public final static int DO_LOGIN = 303;
				public final static int DO_LOGOUT = 304;
			}

			public static final class DCIM {
				public final static int START = 305;
				public final static int STOP = 306;
				public final static int ADD = 307;
			}

			public static final class Home {
				public final static int INIT = 308;
			}
			
		}

		public static final class Transport {

			public static final int MUST_INSTALL_TOR = 400;
			public static final int MUST_START_TOR = 401;
		}

		public class Media {
			public static final int ORIENTATION_PORTRAIT = 1;
			public static final int ORIENTATION_LANDSCAPE = 2;
			
			public static final int TYPE_IMAGE = 400;
			public static final int TYPE_VIDEO = 401;
			public static final int TYPE_JOURNAL = 402;
		}
	}

	public final static class Models {
		public class IUser {
			public final static String PATH_TO_BASE_IMAGE = "path_to_base_image";
			public final static String AUTH_TOKEN = "auth_token";
			public final static String PASSWORD = "password";
			public static final String ALIAS = "alias";
			public static final String BASE_IMAGE = "baseImage";
			public static final String CREDENTIALS = "credentials";
			public static final String SECRET = "secret";
			public static final String SECRET_AUTH_TOKEN = "secretAuthToken";
			public static final String SECRET_KEY = "secretKey";
			public static final String PGP_KEY_FINGERPRINT = "pgpKeyFingerprint";
			public static final String PUBLIC_CREDENTIALS = "publicCredentials";
			public static final String PUBLIC_KEY = "publicKey";
		}
		
		public class IMedia {
			public final static String _ID = "_id";
			
			public class MimeType {
				public final static String IMAGE = "image/jpeg";
				public final static String VIDEO = "video/mp4";
			}
		}
		
		public class ICredentials {
			public final static String PASSWORD_BLOCK = "passwordBlock";
		}

		public class IPendingConnections {
			
		}

		public class IConnection {
			public class Methods {
				public final static String GET = "get";
				public static final String POST = "post";
			}

			public static final String DATA = "data";
			public static final String PARAMS = "params";
		}

		public class IDCIMEntry {
			public final static String FILE_NAME = "fileName";
			public static final String SIZE = "size";
			public static final String URI = "uri";
			public static final String TIME_CAPTURED = "timeCaptured";
			public static final String HASH = "hash";
			public static final String THUMBNAIL = "thumbnail";
			public static final String AUTHORITY = "authority";
		}

		public class IDCIMDescriptor {
			public static final String TAG = "IDCIMDescriptor";
		}
	}

	public final static class IManifest {
		public final static String USER = "informacam_manifest";
		public static final String PREF = "informacam_preferences";		
		public final static String DCIM = "dcimDescriptor";
		public final static String MEDIA = "mediaManifest";
		public static final String PENDING_CONNECTIONS = "pendingConnections";
	}

	public final static class App {
		public final static String LOG = "******************** InformaCam : MAIN ********************";
		
		public static final class Camera {
			public final static String LOG = "******************** InformaCam : CameraActivity ********************";
			public final static String TYPE = "cameraType";
			
			public static final class Type {
				public final static int CAMERA = 500;
				public final static int CAMCORDER = 501;
			}
			
			public static final class Intents {
				public final static String CAMERA = MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA;
				public final static String CAMCORDER = MediaStore.ACTION_VIDEO_CAPTURE;
			}
			
			public static final class Authority {
				public final static Uri CAMERA = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
				public final static Uri CAMCORDER = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
			}
			
			public static final String TAG = "InformaCam.Camera";
			
			public final static List<String> SUPPORTED;
			static {
				List<String> supported = new Vector<String>();
				supported.add("com.sec.android.app.camera");
				SUPPORTED = Collections.unmodifiableList(supported);
			}
		}

		public final static class Transport {
			public final static String LOG = "******************** InformaCam : Transport ********************";
		}

		public final static class Storage {
			public final static String LOG = "******************** InformaCam : Storage ********************";
			public static final String ROOT = "informaCamIOCipher";
			public static final String IOCIPHER = "ic_data.db";
			public static final String DUMP = "informaCam";
			public static final String REVIEW_DUMP = "reviewDump";
			public static final String EXTERNAL_DIR = "InformaCam";
			
			public final static class Type {

				public static final int INTERNAL_STORAGE = 200;
				public static final int IOCIPHER = 201;
				public static final int APPLICATION_ASSET = 202;
				public static final int CONTENT_RESOLVER = 203;
				public static final int FILE_SYSTEM = 204;
				
			}
		}

		public final static class Informa {
			public final static String LOG = "******************** InformaCam : Informa ********************";
		}

		public final static class Crypto {
			public final static String LOG = "******************** InformaCam : Crypto ********************";
			public final static byte[] PASSWORD_SALT = {(byte) 0xA4, (byte) 0x0B, (byte) 0xC8,
			      (byte) 0x34, (byte) 0xD6, (byte) 0x95, (byte) 0xF3, (byte) 0x13};

			public final static class Signatures {
				public final static class Keys {
					public final static String SIGNATURE = "dataSignature";
				}
			}
		}

		public final static class ImageCapture {
			public final static String LOG = "******************** InformaCam : ImageCapture ********************";
			public final static int ROUTE = Codes.Routes.IMAGE_CAPTURE;
		}
	}
}
