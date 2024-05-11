using System;
using System.Collections;
using TMPro;
using UnityEngine;

namespace PCP.LibLime
{
	public class LimePluginManager : MonoBehaviour
	{
		public static LimePluginManager Instance;

		private readonly string mTag = "LimePluginManager";
		private readonly float laodingTimeout = 10f;
		private AndroidJavaObject mPluginManager;
		private StreamManager mStreamManager;
		private PcManager mPcManager;
		private AppManager mAppManger;
		private bool shouldResume = false;
		public enum PluginType
		{
			Pc,
			App,
			Stream,
		}
		public bool Blocking { private set; get; } = true;

		//LifeCycle///////////
		private void Awake()
		{
			if (Instance != null)
			{
				Debug.LogError(mTag + ":Double Instance Found");
				return;
			}
			Instance = this;
			mStreamManager = GetComponent<StreamManager>();
			mPcManager = GetComponent<PcManager>();
			mAppManger = GetComponent<AppManager>();
			OnJavaCallback += ChangeUIHandler;
		}

		private void Start()
		{
			CreatePluginObject();
		}
		private void CreatePluginObject()
		{
			mPluginManager = new AndroidJavaObject("com.liblime.PluginManager");
			mPluginManager.Call("Init");
			Blocking = false;
		}

		private void OnApplicationPause(bool pause)
		{
			if (pause)
			{
				if (mStreamManager.IsInitialized)
					shouldResume = true;
				DoReset(false);
			}
			else if (shouldResume)
			{
				shouldResume = false;
				Debug.Log("Resuming Last Stream");
				StartLastApp();
			}
		}


		private void OnDestroy()
		{
			/* StopAllCoroutines(); */
			/* DestroyAllPluginObjects(); */
			DoReset(false);
			mPluginManager.Call("Destroy");
			mPluginManager.Dispose();
			mPluginManager = null;
		}
		//Plugin Methods///////////
		public void StartPc()
		{
			mPluginManager.Call("StartPC");
			StartManager(PluginType.Pc);
		}
		public bool IsAlive()
		{
			if (mPluginManager == null)
			{
				Debug.LogError(mTag + " is null");
				return false;
			}
			try
			{
				mPluginManager.Call("Poke");
			}
			catch (Exception e)
			{
				Debug.LogWarning(mTag + " poking failed:" + e.Message);
				return false;
			}
			return true;
		}
		public bool HasRunningPlugin()
		{
			return mPluginManager.Call<bool>("HasRunningPlugin");
		}

		private void DestroyPluginObject(PluginType t)
		{
			mPluginManager.Call("DestroyPlugin", (int)t);
		}
		public void DestroyAllPluginObjects()
		{
			mPluginManager.Call("DestroyAllPlugins");
		}

		public void DoReset(bool wait = true)
		{
			if (CheckBlocking())
				return;
			//Check if there is any running manager
			StopAllCoroutines();
			StopManagers();
			ResetPlugin(wait);
		}
		public void ResetPlugin(bool wait)
		{
			Blocking = true;
			if (mPluginManager == null)
			{
				Debug.LogError("PluginManager is null");
				return;
			}
			DestroyAllPluginObjects();
			if (wait)
				StartCoroutine(TaskResetPlugin());
			else
			{
				Debug.Log("Reset Plugin without waiting");
				Blocking = false;
				Blocker.SetActive(false);
			}
		}
		private IEnumerator TaskResetPlugin()
		{
			yield return new WaitForEndOfFrame();
			while (HasRunningPlugin())
			{
				yield return new WaitForEndOfFrame();
			}
			Debug.Log("All Plugin Destroyed");
			Blocking = false;
			Blocker.SetActive(false);
		}
		//Manager Methods///////////
		public void StartManager(PluginType t)
		{
			/* if (t == PluginType.Pc) */
			/* 	mPluginManager.Call("StartPC"); */
			StartCoroutine(InitManager(t));
		}
		private IEnumerator InitManager(PluginType t)
		{
			Debug.Log("Try to find Plugin:" + t);
			if (CheckBlocking())
				yield break;
			float timer = 0;
			//TODO: if we are using plugin callback to start manager, we can remove this.but what if
			//plugin failed to load?
			AndroidJavaObject o = mPluginManager.Call<AndroidJavaObject>("GetPlugin", (int)t);
			while (o == null)
			{
				if (timer > laodingTimeout)
				{
					Debug.LogError(t.ToString() + "Loading Timeout:Cannot get plugin");
					yield break;
				}
				Debug.Log("Foudning plugin:" + t + "Time:" + timer);
				yield return new WaitForSeconds(1);
				o = mPluginManager.Call<AndroidJavaObject>("GetPlugin", (int)t);
				timer += Time.deltaTime;
			}
			switch (t)
			{
				case PluginType.Pc:
					mPcManager.Init(o, this);
					break;
				case PluginType.Stream:
					mStreamManager.Init(o, this);
					break;
				case PluginType.App:
					mAppManger.Init(o, this);
					break;
				default:
					break;
			}
		}
		private void StopManagers()
		{
			mAppManger.enabled = false;
			mPcManager.enabled = false;
			mStreamManager.enabled = false;
			Debug.Log("All Managers Stopped");
		}
		//Utils
		private bool CheckBlocking()
		{
			if (Blocking)
			{
				Debug.LogWarning(mTag + " is blocking");
				return true;
			}
			return false;
		}
		//Message Handlers
		public delegate void JavaCallbackHandler(string msg);
		public JavaCallbackHandler OnJavaCallback;
		public void OnCallback(string msg)
		{
			Debug.Log(mTag + "JavaCallback Received:" + msg);
			OnJavaCallback?.Invoke(msg);
		}

		//Dialog and Notification
		public GameObject Blocker;
		public GameObject DialogWindow;
		public TMP_Text DialogText;
		public void OnDialog(string m)
		{
			string[] msglsit = m.Split('|');
			string msg = msglsit[0];
			int level = int.Parse(msglsit[1]);
			DialogText.text = msg;
			DialogWindow.SetActive(true);
			switch (level)
			{
				case 0:
					/* MessageManager.Instance.Info(msg); */
					break;
				case 1:
					/* MessageManager.Instance.Warn(msg); */
					break;
				case 2:
					/* MessageManager.Instance.Error(msg); */
					DoReset();
					break;
				default:
					break;
			}
		}
		public NotificationPool notificationPool;

		public void OnNotify(string message)
		{
			Notification notification = notificationPool.Get();
			if (notification != null)
			{
				notification.Display(message);
			}
		}
		//Shortcut
		internal void StartShortcut(ShortcutData sd)
		{
			StartManager(PluginType.Stream);
			mPluginManager.Call("DoShortcut", sd.uuid, sd.appName, sd.appID.ToString());
		}
		//TODO:use file and array to store the last app,should check out if can access the persistent
		//data path
		/* internal void UpdateLastApp(ShortcutData sd) */
		/* { */
		/* 	PlayerPrefs.SetString("LastApp", JsonUtility.ToJson(sd)); */
		/* } */
		public void StartLastApp()
		{
			string la = PlayerPrefs.GetString("LastApp", "");
			if (la == "")
				return;
			Blocker.SetActive(true);
			StartShortcut(JsonUtility.FromJson<ShortcutData>(la));
		}
		//UI
		public void ChangeUIHandler(string msg)
		{
			if (!msg.StartsWith("UI"))
				return;
			msg = msg[2..];
			switch (msg)
			{
				case "PC":
					ChangeUIRoot(PluginType.Pc);
					break;
				case "APP":
					ChangeUIRoot(PluginType.App);
					break;
				case "STM":
					ChangeUIRoot(PluginType.Stream);
					break;
				default:
					break;
			}
			Debug.Log("UI Changed to:" + msg);
		}
		public void ChangeUIRoot(PluginType t)
		{
			Debug.Log("ChangeUIRoot:" + t);
			//TODO: need a map for that
			if (t != PluginType.Pc)
				mPcManager.enabled = false;
			if (t != PluginType.App)
				mAppManger.enabled = false;
			if (t != PluginType.Stream)
				mStreamManager.enabled = false;
		}
		//TRY Debug
		public void TestDialog(bool t)
		{
			mPluginManager.Call("TestDialog", t);
		}
		public void TestNotify(string m)
		{
			mPluginManager.Call("TestNotify", m);
		}
	}
}
