using System.Collections;
using UnityEngine;

namespace PCP.LibLime
{
	/// <summary>
	/// Base class for all Plugin Objects
	/// </summary>
	public class BasePluginBride : MonoBehaviour
	{
		protected string mTag;
		public LimePluginManager.PluginType Type { get; protected set; }
		[SerializeField] protected GameObject mPanel;
		public GameObject Blocker;
		protected LimePluginManager.JavaCallbackHandler mCallBackHanlder;
		private void OnCallback(string m) => mCallBackHanlder?.Invoke(m);
		public enum PluginState
		{
			NONE,
			INITIALISING,
			INITIALIZED,
			WORKING,
		}
		protected AndroidJavaObject mPlugin;
		protected LimePluginManager mPluginManager;
		/* public IntPtr RawObject { get; protected set; } */

		private readonly float lodingTimeout = 10f;

		public PluginState State { get; protected set; }
		public bool IsInitialized => State == PluginState.INITIALIZED;

		public GameObject GameObject => gameObject;

		public void Init(AndroidJavaObject o, LimePluginManager m)
		{
			if (State != PluginState.NONE)
			{
				Debug.LogError(mTag + "already Started");
				return;
			}
			MessageManager.Instance.Info(mTag + " Initailizing");
			Blocker.SetActive(true);
			mPluginManager = m;
			mPluginManager.OnJavaCallback += OnCallback;
			enabled = true;
			mPlugin = o;
			/* RawObject = o.GetRawObject(); */
			_ = StartCoroutine(InitPlugin());
		}
		private IEnumerator InitPlugin()
		{
			State = PluginState.INITIALISING;
			float loadingTime = Time.time + lodingTimeout;
			yield return new WaitForEndOfFrame();

			if (mPlugin == null)
			{
				Debug.LogError(mTag + ": Can't Find Plugin");
				yield break;
			}

			while (mPlugin.Call<bool>("IsInitialized") == false)
			{
				if (Time.time > loadingTime)
				{
					//TODO:need handle object destroy?
					Debug.LogError(mTag + ": Plugin Loading Timeout");
					yield break;
				}
				yield return null;
			}
			State = PluginState.INITIALIZED;
			OnCreate();
			if (mPanel != null)
				mPanel.SetActive(true);
			Blocker.SetActive(false);
			mPluginManager.ChangeUIRoot(Type);
			MessageManager.Instance.Info(mTag + "Initialized");
		}

		protected virtual void OnCreate() { }
		protected virtual void OnStop() { }
		protected virtual void OnDestroy()
		{
			StopManager();
		}
		protected virtual void OnDisable()
		{
			StopManager();
		}
		private void StopManager()
		{
			if (State == PluginState.NONE)
				return;
			if (mPanel != null)
				mPanel.SetActive(false);
			StopAllCoroutines();
			OnStop();
			if (mPlugin != null)
			{
				mPlugin?.Dispose();
				mPlugin = null;
			}
			State = PluginState.NONE;
			mPluginManager.OnJavaCallback -= OnCallback;
			mPluginManager = null;
			MessageManager.Instance.Info(mTag + "Stopped");
		}
		///////////Methods////////////
		///PERF:maybe a interface
		public bool IsAlive()
		{
			if (mPlugin == null)
			{
				Debug.LogError(mTag + "is null");
				return false;
			}
			try
			{
				mPlugin.Call("Poke");
			}
			catch (System.Exception e)
			{
				Debug.LogError(mTag + " poking failed:" + e.Message);
				return false;
			}
			return true;
		}
		//////////Ui/////////////
	}
}
