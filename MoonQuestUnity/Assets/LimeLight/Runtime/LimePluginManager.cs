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
		private IEnumerator Destroy()
		{
			mPluginManager.Call("Destroy");
			yield return new WaitForSeconds(1);
			while (IsAlive())
			{
				yield return new WaitForSeconds(1);
				Debug.Log("Waiting for Plugin to Destroy");
			}
			mPluginManager.Dispose();
			mPluginManager = null;
		}
		private void OnDestroy()
		{
			StartCoroutine(Destroy());
		}

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
		//PERF:!!Change This to a hashmap?
		public void DestroyPluginObject(string pluginName)
		{
			mPluginManager.Call("DestroyPlugin", pluginName);
		}
		public void CreatePluginObject()
		{
			mPluginManager = new AndroidJavaObject("com.liblime.PluginManager");
			mPluginManager.Call("Init");
		}
		private IEnumerator TaskRestartPlugin()
		{
			if (mPluginManager == null || !IsAlive())
			{
				Debug.LogError("PluginManager is null");
				yield break;
			}
			StartCoroutine(Destroy());
			yield return new WaitForSeconds(1);
			while (HasRunningPlugin() || IsAlive())
			{
				yield return new WaitForSeconds(1);
			}
			CreatePluginObject();
		}
		//Creating PluginObjects
		public RawImage image;
		public void CreateStreamObject()
		{
			//TRY
			int w = 3440;
			int h = 1440;
			mStreamManager = gameObject.AddComponent<StreamManager>();
			mStreamManager.Init(mPluginManager.Call<AndroidJavaObject>("GetStreamPlugin"), image, w, h);
		}

		//TRY Debug
		public void FakeStart()
		{
			mPluginManager.Call("FakeStart");
		}
		public void DumbyStart()
		{
			FakeStart();
			StartCoroutine(StartPlugin());

		}
		private IEnumerator StartPlugin()
		{
			int w = 3440;
			int h = 1440;
			mStreamManager = gameObject.AddComponent<StreamManager>();
			AndroidJavaObject o = mPluginManager.Call<AndroidJavaObject>("GetStreamPlugin");
			while (o == null)
			{
				yield return new WaitForSeconds(1);
				o = mPluginManager.Call<AndroidJavaObject>("GetStreamPlugin");
			}
			mStreamManager.Init(o, image, w, h);
		}
		public void RestartPlugin()
		{
			StartCoroutine(TaskRestartPlugin());
		}

	}
}
