using System.Collections;
using UnityEngine;

namespace PCP.LibLime
{
	/// <summary>
	/// Base class for all Plugin Objects
	/// </summary>
	public abstract class BasePluginBride : MonoBehaviour
	{
		protected string mTag;
		public enum PluginState
		{
			NONE,
			INITIALISING,
			INITIALIZED,
		}
		protected AndroidJavaObject mPlugin;
		/* public IntPtr RawObject { get; protected set; } */

		private float lodingTimeout = 10f;

		public PluginState State { get; protected set; }
		public bool IsInitialized => State == PluginState.INITIALIZED;

		public GameObject GameObject => gameObject;

		public void Init(AndroidJavaObject o)
		{
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
			OnCreate();
			State = PluginState.INITIALIZED;
		}

		protected abstract void OnCreate();
		protected virtual void OnDestroy()
		{
			mPlugin.Dispose();
			State = PluginState.NONE;
		}

		///////////Methods////////////
		///
		//PERF:maybe a interface
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

	}
}
