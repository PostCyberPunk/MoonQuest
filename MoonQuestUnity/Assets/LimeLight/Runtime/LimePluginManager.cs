using System;
using System.Collections;
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

		private void OnDestroy()
		{
			StopAllCoroutines();
			DestroyAllPluginObjects();
			mPluginManager.Call("Destroy");
			mPluginManager.Dispose();
			mPluginManager = null;
		}
		//Plugin Methods///////////
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

		public void ResetPlugin()
		{
			StartCoroutine(TaskResetPlugin());
		}
		private IEnumerator TaskResetPlugin()
		{
			Blocking = true;
			if (mPluginManager == null)
			{
				Debug.LogError("PluginManager is null");
				yield break;
			}
			DestroyAllPluginObjects();
			yield return new WaitForSeconds(1);
			while (HasRunningPlugin())
			{
				yield return new WaitForSeconds(1);
			}
			Blocking = false;
		}
		//Manager Methods///////////
		public void StartManager(PluginType t)
		{
			if (t == PluginType.Pc)
				mPluginManager.Call("StartPC");
			StartCoroutine(InitManager(t));
		}
		private IEnumerator InitManager(PluginType t)
		{
			if (CheckBlocking())
				yield break;
			float timer = 0;
			AndroidJavaObject o = mPluginManager.Call<AndroidJavaObject>("GetPlugin", (int)t);
			while (o == null)
			{
				if (timer > laodingTimeout)
				{
					Debug.LogError(t.ToString() + "Loading Timeout:Cannot get plugin");
					yield break;
				}
				yield return new WaitForSeconds(1);
				o = mPluginManager.Call<AndroidJavaObject>("GetPlugin", (int)t);
				timer += Time.deltaTime;
			}
			switch (t)
			{
				case PluginType.Pc:
					mPcManager.Init(o);
					break;
				case PluginType.Stream:
					mStreamManager.Init(o);
					break;
				case PluginType.App:
					mAppManger.Init(o);
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
		public static void JavaCallback(string msg) => Instance.OnJavaCallback?.Invoke(msg);

		//TODO: make
		public static void OnDialog(string m)
		{
			string[] msglsit = m.Split('|');
			string msg = msglsit[0];
			int level = int.Parse(msglsit[1]);
			switch (level)
			{
				case 0:
					MessageManager.Info(msg);
					break;
				case 1:
					MessageManager.Warn(msg);
					break;
				case 2:
					MessageManager.Error(msg);
					break;
				default:
					break;
			}
		}
		//TRY Debug
		public void StartPc()
		{
			StartManager(PluginType.Pc);
		}
		public void DummyReset()
		{
			if (CheckBlocking())
				return;
			StopManagers();
			ResetPlugin();
		}
	}
}
