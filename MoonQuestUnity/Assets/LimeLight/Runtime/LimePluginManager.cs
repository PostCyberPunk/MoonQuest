using System;
using System.Collections;
using UnityEngine;
using UnityEngine.UI;

namespace PCP.LibLime
{
	public class LimePluginManager : MonoBehaviour
	{
		public static LimePluginManager Instance;

		private readonly string mTag = "LimePluginManager";
		private AndroidJavaObject mPluginManager;
		private StreamManager mStreamManager;
		private PcManager mPcManager;
		private enum PluginType
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
		}

		private void Start()
		{
			CreatePluginObject();
		}
		public void CreatePluginObject()
		{
			mPluginManager = new AndroidJavaObject("com.liblime.PluginManager");
			mPluginManager.Call("Init");
			Blocking = false;
		}

		private void OnDestroy()
		{
			mPluginManager.Call("Destroy");
			mPluginManager.Dispose();
			mPluginManager = null;
		}
		//Plugin Methods///////////
		public bool IsAlive()
		{
			Destroy(mStreamManager);
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

		public void DestroyPluginObject(string pluginName)
		{
			mPluginManager.Call("DestroyPlugin", pluginName);
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
			Blocking = false;
			if (mPluginManager == null || !IsAlive())
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
		public RawImage image;

		private IEnumerator StartStreamManager()
		{
			int w = 3440;
			int h = 1440;
			mStreamManager = gameObject.AddComponent<StreamManager>();
			AndroidJavaObject o = mPluginManager.Call<AndroidJavaObject>("GetPlugin", (int)PluginType.Stream);
			while (o == null)
			{
				yield return new WaitForSeconds(1);
				o = mPluginManager.Call<AndroidJavaObject>("GetPlugin", (int)PluginType.Stream);
			}
			mStreamManager.Init(o, image, w, h);
		}

		private IEnumerator StartPcMananger()
		{
			mPcManager = gameObject.AddComponent<PcManager>();
			AndroidJavaObject o = mPluginManager.Call<AndroidJavaObject>("GetPlugin", (int)PluginType.Pc);
			while (o == null)
			{
				yield return new WaitForSeconds(1);
				Debug.Log("PcManager is null");
				o = mPluginManager.Call<AndroidJavaObject>("GetPlugin", (int)PluginType.Pc);
			}
			mPcManager.Init(o);
		}
		private void DestroyManagers()
		{
			Destroy(mStreamManager);
			Destroy(mPcManager);
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
		//TRY Debug
		public void DumbyStart()
		{
			if (CheckBlocking())
				return;
			mPluginManager.Call("Start");
			StartCoroutine(StartPcMananger());
			StartCoroutine(StartStreamManager());
		}
		public void DummyReset()
		{
			if (CheckBlocking())
				return;
			DestroyManagers();
			ResetPlugin();
		}
	}
}
