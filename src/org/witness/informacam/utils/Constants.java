package org.witness.informacam.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import org.witness.informacam.models.j3m.ILogPack;

import android.net.Uri;
import android.os.Environment;
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
		public void onSubFragmentInitialized();
	}
	
	public interface SuckerCacheListener {
		public void onUpdate(long timestamp, ILogPack ILogPack);
		public long onUpdate(ILogPack ILogPack);
	}
	
	public interface HttpUtilityListener {
		public void onOrbotRunning();
	}
	
	public interface MetadataEmbededListener {
		public void onMetadataEmbeded(info.guardianproject.iocipher.File version);
		public void onMetadataEmbeded(java.io.File version);
	}

	public final static class Actions {
		public final static String INIT = "org.witness.informacam.action.INIT";
		public final static String SHUTDOWN = "org.witness.informacam.action.SHUTDOWN";
		public final static String ASSOCIATE_SERVICE = "org.witness.informacam.action.ASSOCIATE_SERVICE";
		public static final String DISASSOCIATE_SERVICE = "org.witness.informacam.action.DISASSOCIATE_SERVICE";
		public static final String UPLOADER_UPDATE = "org.witness.informacam.action.UPLOADER_UPDATE";
		public static final String CAMERA = "android.media.action.IMAGE_CAPTURE";
		public static final String INFORMACAM_START = "org.witness.informacam.action.INFORMACAM_START";
		public static final String INFORMACAM_STOP = "org.witness.informacam.action.INFORMACAM_STOP";
		public static final String INFORMA_START = "org.witness.informacam.action.INFORMA_SERVICE_START";
		public static final String INFORMA_STOP = "org.witness.informacam.action.INFORMA_SERVICE_STOP";
		public static final String PERSISTENT_SERVICE = "org.witness.informacam.action.PERSISTENT_SERVICE";
	}

	public final static class Codes {
		public final static class Routes {
			public final static int IMAGE_CAPTURE = 100;
			public final static int SIGNATURE_SERVICE = 101;
			public static final int IO_SERVICE = 102;
			public static final int UPLOADER_SERVICE = 103;
			public static final int RETRY_SAVE = 104;
			public static final int RETRY_GET = 105;
			public static final int INFORMA_SERVICE = 106;
			public static final int BACKGROUND_PROCESSOR = 107;
		}

		public final static class Keys {
			public final static String SERVICE = "service";
			public static final String IV = "iv";
			public static final String VALUE = "value";
			public static final String UPLOADER = "uploader";
			public static final String DCIM_DESCRIPTOR = "dcimDescriptor";
			public static final String BATCH_EXPORT_FINISHED = "batchExportFinished";

			public static final class UI {
				public static final String PROGRESS = "progress";
				public static final String UPDATE = "update";
			}
		}

		public final static class Extras {
			public final static String WIZARD_SUPPLEMENT = "wizard_supplement";
			public static final String MESSAGE_CODE = "message_code";
			public static final String RETURNED_MEDIA = "informacam_returned_media";
			public static final String INSTALL_NEW_KEY = "install_ictd_uri";
			public static final String LOGOUT_USER = "logout_user";
			public static final String RESTRICT_TO_PROCESS = "restrict_to_process";
			public static final String CAMERA_TYPE = "camera_type";
			public static final String GPS_FAILURE = "gps_failure";
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
	
	public final static class Time {
		public final static String LOG = "**************** InformaCam: TIME ****************";

		public final static class DateFormats {
			public static final String EXPORT_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
			public static final String EXIF_DATE_FORMAT = "yyyy:MM:dd HH:mm:ss";
		}

		public static final class Keys {

			public static final String RELATIVE_TIME = "mediaRelativeTimestamp";

		}
	}
	
	public final static class Forms {
		public final static String LOG = "**************** InformaCam: Forms ****************";
	}
	
	public final static class Ffmpeg {
		public final static String LOG = "**************** InformaCam: FFMPEG ****************";
	}

	public final static class Suckers {
		public final static String LOG = "******************** InformaCam : Suckers ********************";
		public static final int GPS_WAIT_MAX = 60;

		public final static class CaptureEvent {
			public final static int METADATA_CAPTURED = 272;
			public final static int MEDIA_OPENED = 273;
			public final static int REGION_GENERATED = 274;
			public final static int MEDIA_SAVED = 275;
			public final static int SENSOR_PLAYBACK = 271;
			public final static int TIMESTAMPS_RESOLVED= 270;
			public final static int FORM_EDITED = 269;
			
			
			public final static class Keys {
				public final static String USER_ACTION = "userActionReported";
				public final static String TYPE = "captureTypes";
				public final static String MATCH_TIMESTAMP = "captureEventMatchTimestamp";
				public final static String TIMESTAMP = "captureEventTimestamp";
				public final static String ON_VIDEO_START = "timestampOnVideoStart";
				public final static String MEDIA_CAPTURE_COMPLETE = "mediaCapturedComplete";
				public final static String METADATA_CAPTURED = "metadataCaptured";
				public final static String REGION_LOCATION_DATA = "regionLocationData";
			}
		}

		public final static class Phone {
			public final static long LOG_RATE = 20000L;

			public final static class Keys {
				public static final String CELL_ID = "cellTowerId";
				public static final String BLUETOOTH_DEVICE_ADDRESS = "bluetoothDeviceAddress";
				public static final String BLUETOOTH_DEVICE_NAME = "bluetoothDeviceName";
				public static final String IMEI = "IMEI";
				public static final String VISIBLE_WIFI_NETWORKS = "visibleWifiNetworks";
				public static final String BSSID = "bssid";
				public static final String SSID = "ssid";
			}
		}

		public final static class Accelerometer {
			public final static long LOG_RATE = 500L;
			
			public final static class Keys {
				public static final String ACC = "acc";
				public static final String ORIENTATION = "orientation";
				public static final String LIGHT = "light";
				public static final String LIGHT_METER_VALUE = "lightMeterValue";
				public static final String X = "acc_x";
				public static final String Y = "acc_y";
				public static final String Z = "acc_z";
				public static final String PITCH = "pitch";
				public static final String ROLL = "roll";
				public static final String AZIMUTH = "azimuth";
			}
		}

		public final static class Geo {
			public final static long LOG_RATE = 10000L;

			public final static class Keys {
				public static final String GPS_COORDS = "gps_coords";
			}
		}
	}

	public final static class Models {
		public static final String _ID = "_id";
		public static final String _REV = "_rev";		
		
		public class LogCache {
			public final static String CACHE = "cache";
			public final static String TIME_OFFSET = "timeOffset";
		}
		
		public class INotification {
			public class Type {
				public final static int NEW_KEY = 600;
				public static final int KEY_SENT = 601;
				public static final int EXPORTED_MEDIA = 602;
				public static final int SHARED_MEDIA = 603;
			}

			public static final String ID = "notification_id";
			public static final String CLASS = "handler_message_type";
		}
		
		public class IGenealogy {
			public class OwnershipType {
				public final static int INDIVIDUAL = 400;
				public final static int ORGANIZATION = 401;
			}
		}
		
		public class IRegion {
			public final static String REGION_BOUNDS = "region_bounds";
			public static final String REGION_COORDINATES = "region_coordinates";
			public static final String REGION_DIMENSIONS = "region_dimensions";
			public static final String REGION_TIMESTAMPS = "region_timestamps";
			
			public static final String DISPLAY_TOP = "displayTop";
			public static final String DISPLAY_LEFT = "displayLeft";
			public static final String DISPLAY_WIDTH = "displayWidth";
			public static final String DISPLAY_HEIGHT = "displayHeight";
			public static final String BOUNDS = "bounds";
			
			public class Bounds {
				public final static String TOP = "top";
				public final static String LEFT = "left";
				public final static String WIDTH = "width";
				public final static String HEIGHT = "height";
				public final static String START_TIME = "startTime";
				public final static String END_TIME = "endTime";
				public static final String DURATION = "duration";
			}
		}
		
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
			public static final String BELONGS_TO_USER = "belongs_to_user";
		}

		public class IMediaManifest {
			public class Sort {
				public final static int DATE_DESC = 1;
				public final static int DATE_ASC = 4;
				public final static int TYPE_PHOTO = 2;
				public final static int TYPE_VIDEO = 3;
				public final static int LOCATION = 0;
				public final static String IS_SHOWING = "isShowing";
			}
		}
		
		public class INotificationManifest {
			public class Sort {
				public final static int DATE_DESC = IMediaManifest.Sort.DATE_DESC;
				public final static int DATE_ASC = IMediaManifest.Sort.DATE_ASC;
			}
		}

		public class IMedia {
			public final static String _ID = "_id";
			public static final String J3M = "j3m";
			public static final String J3M_DESCRIPTOR = "j3m_descriptor";
			public static final String VERSION = "versionForExport";
			
			public class Flags {
				public final static String IS_NEW = "isNew";
			}
			
			public class ILog {
				public final static String ATTACHED_MEDIA = "attachedMedia";
				public final static String IS_CLOSED = "isClosed";
				public final static String START_TIME = "startTime";
				public final static String END_TIME = "endTime";
			}
			
			public class Data {

				public static final String SENSOR_PLAYBACK = "sensorPlayback";
				
			}
			
			public class Image {
				public static final String BITMAP = "bitmap";
			}
			
			public class Video {
				public static final String VIDEO = "video";
			}

			public class MimeType {
				public final static String IMAGE = "image/jpeg";
				public final static String VIDEO = "video/mp4";
				public static final String LOG = "informacam/log";
			}

			public class j3m {
				public final static String DATA = "data";
				public final static String GENEALOGY = "genealogy";
				public final static String INTENT = "intent";
				public static final String SIGNATURE = "signature";
				public static final String SIZE = "size";
				public static final String HASH = "hash";
				public static final String FILE_NAME = "file_name";
			}
			
			public class TempKeys {
				public final static String IS_SELECTED = "isBatchSelected";
				public final static String SHOULD_SHOW = "shouldShow";
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
			public static final String _ID = "_id";
			public static final String _REV = "_rev";
			public static final String BELONGS_TO_USER = "belongs_to_user";
			public static final String BYTE_RANGE = "byte_range";
			
			public static final int MAX_TRIES = 10;
			public static final String PATH_TO_NEXT_CONNECTION_DATA = "pathToNextConnectionData";
			public static final String BYTES_TRANSFERRED = "bytes_transferred";
			public static final String BYTES_TRANSFERRED_VERIFIED = "bytes_transferred_verified";
			public static final String PROGRESS = "progress";
			public static final String PARENT = "parent";
			
			public class Type {
				public static final int NONE = 799;
				public static final int MESSAGE = 800;
				public static final int SUBMISSION = 801;
				public static final int UPLOAD = 802;
			}
			
			public class CommonParams {
				public static final String MESSAGE_TO = "message_to";
				public static final String MESSAGE_TIME = "message_time";
				public static final String MESSAGE_CONTENT = "message_content";
			}
			
			public class Routes {
				public static final String EXPORT = "export/";
				public static final String MESSAGES = "messages/";
				public static final String SUBMISSIONS = "submissions/";
				public static final String UPLOAD = "upload/";
			}
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

		public class IResult {
			public final static String DATA = "data";
			public final static String REASON = "reason";
			public static final String RESPONSE_CODE = "response_code";
			public static final String CONTENT = "content";
			public static final String RESULT_CODE = "result";
			
			public class ResponseCodes {
				public static final int DOWNLOAD_ASSET = 43;
				public final static int INIT_USER = 44;
				public static final int INSTALL_ICTD = 45;
				public static final int UPLOAD_SUBMISSION = 46;
				public static final int UPLOAD_CHUNK = 47;
			}
		}

		public class ITransportData {
			public final static String UPLOAD = "upload";
			public static final String FILE = "file";
			public static final String _ID = "_id";
			public static final String _REV = "_rev";
		}

		public class IIdentity {
			public final static String SOURCE = "source";
			public static final String CREDENTIALS = "credentials";
		}

		public class IOrganization {

			public static final String ADDRESS = "address";
			public static final String CITY = "city";
			public static final String STATE = "state";
			public static final String ZIP = "zip";
			public static final String PHONE = "phone";
			public static final String FAX = "fax";
			public static final String ORGANIZATION_DETAILS = "organizationDetails";
			public static final String NEW_REQUEST = "newRequest";
			public static final String METHOD = "method";
			public static final String PORT = "port";
			public static final String REQUEST_URL = "requestUrl";
			
		}

		public class ITransportCredentials {
			public static final String PASSWORD = "password";
		}
	}

	public final static class IManifest {
		public final static String USER = "informacam_manifest";
		public static final String PREF = "informacam_preferences";		
		public final static String DCIM = "dcimDescriptor";
		public final static String MEDIA = "mediaManifest";
		public static final String PENDING_CONNECTIONS = "pendingConnections";
		public static final String FORMS = "installedForms";
		public static final String ORGS = "installedOrganizations";
		public static final String KEY_STORE_MANIFEST = "keystoreManifest";
		public static final String KEY_STORE = "keystore.jks";
		public static final String CACHES = "informaCaches";
		public static final String NOTIFICATIONS = "notificationsManifest";
		public static final String DEX = "dexDump";
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
				public final static String CAMERA_SIMPLE = MediaStore.ACTION_IMAGE_CAPTURE;
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
				supported.add("com.android.camera");
				supported.add("com.google.android.gallery3d");
				SUPPORTED = Collections.unmodifiableList(supported);
			}
		}

		public final static class Transport {
			public final static String LOG = "******************** InformaCam : Transport ********************";
			
			public final static class Results {
				public final static String OK_BUT_FAIL = "500: Internal Server Error";
				public final static String OK = "200";
				public final static String[] FAIL = {"404", "500"};
			}
		}

		public final static class Background {
			public final static String LOG = "******************** InformaCam : BackgroundProcessor ********************";
		}
		
		public final static class Storage {
			public final static String LOG = "******************** InformaCam : Storage ********************";
			public static final String ROOT = "informaCamIOCipher";
			public static final String IOCIPHER = "ic_data.db";
			public static final String DUMP = "informaCam";
			public static final String REVIEW_DUMP = "reviewDump";
			public static final String EXTERNAL_DIR = Environment.getExternalStorageDirectory().getAbsolutePath() + "/InformaCam";
			public static final String FORM_ROOT = "forms";
			public static final String ORGS_ROOT = "organizations";
			public static final String LOG_DUMP = "iLogs";
			public static final String ATTACHED_MEDIA = "attachedMedia";

			public final static class Type {

				public static final int INTERNAL_STORAGE = 200;
				public static final int IOCIPHER = 201;
				public static final int APPLICATION_ASSET = 202;
				public static final int CONTENT_RESOLVER = 203;
				public static final int FILE_SYSTEM = 204;

			}
			
			public final static class ICTD {
				public final static List<String> ZIP_OMITABLES;
				static {
					List<String> zip_omitables = new ArrayList<String>();
					zip_omitables.add("__MACOSX");
					zip_omitables.add("DS_Store");
					ZIP_OMITABLES = Collections.unmodifiableList(zip_omitables);
				}
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
