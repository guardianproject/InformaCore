package org.witness.informacam.utils;


import android.os.Message;
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
		public static final String UPLOADER_UPDATE = "org.witness.informacam.action.UPLOADER_UPDATE";;
	}

	public final static class Codes {
		public final static class Routes {
			public final static int IMAGE_CAPTURE = 100;
			public final static int SIGNATURE_SERVICE = 101;
			public static final int IO_SERVICE = 102;
			public static final int UPLOADER_SERVICE = 103;
		}

		public final static class Keys {
			public final static String SERVICE = "service";
			public static final String IV = "iv";
			public static final String VALUE = "value";
			public static final String UPLOADER = "uploader";

			public static final class UI {
				public static final String PROGRESS = "progress";
				public static final String UPDATE = "update";
			}
		}
		
		public final static class Extras {
			public final static String WIZARD_SUPPLEMENT = "wizard_supplement";
			public static final String MESSAGE_CODE = "message_code";
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
			
		}

		public static final class Transport {

			public static final int MUST_INSTALL_TOR = 400;
			public static final int MUST_START_TOR = 401;
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
		
		public class ICredentials {
			public final static String PASSWORD_BLOCK = "passwordBlock";
		}

		public class IPendingConnections {
			public static final String PATH = "pendingConnections";
		}

		public class IConnection {
			public class Methods {
				public final static String GET = "get";
			}

			public static final String DATA = "data";
			public static final String PARAMS = "params";
		}
	}

	public final static class IManifest {
		public final static String PATH = "informacam_manifest";
		public static final String PREF = "informacam_preferences"; 
	}

	public final static class App {
		public final static String LOG = "******************** InformaCam : MAIN ********************";

		public final static class Transport {
			public final static String LOG = "******************** InformaCam : Transport ********************";
		}

		public final static class Storage {
			public final static String LOG = "******************** InformaCam : Storage ********************";
			public static final String ROOT = "informaCamIOCipher";
			public static final String IOCIPHER = "iocipher.db";
			public static final String DUMP = "informaCam";
			
			public final static class Type {

				public static final int INTERNAL_STORAGE = 200;
				public static final int IOCIPHER = 201;
				public static final int APPLICATION_ASSET = 202;
				
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
