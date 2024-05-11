using System;
using UnityEngine;
using UnityEngine.UI;
namespace PCP.LibLime
{
	public class StreamManager : BasePluginBridge
	{
		[SerializeField] private RawImage mRawImage;
		private Texture mPausingTex;
		private Vector2 mPausingSize;
		private int mTexWidth;
		private int mTexHeight;
		private IntPtr mRawObject;

		private void Awake()
		{
			Type = LimePluginManager.PluginType.Stream;
			mTag = "StreamManager";
			mPausingTex = mRawImage.texture;
			mPausingSize = mRawImage.rectTransform.sizeDelta;
		}

		protected override void OnCreate()
		{
			GetResolution();
			Debug.Log(mTag + ":Resolution " + mTexWidth + "x" + mTexHeight);
			mRawObject = mPlugin.GetRawObject();
			mRawImage.rectTransform.sizeDelta = new Vector2(mTexWidth, mTexHeight);
			mRawImage.texture = new Texture2D(mTexWidth, mTexHeight, TextureFormat.ARGB32, false, false)
			{
				filterMode = FilterMode.Trilinear,
				anisoLevel = 16
			};
			SaveLastApp();
		}
		protected override void OnStop()
		{
			mRawImage.texture = mPausingTex;
			mRawImage.rectTransform.sizeDelta = mPausingSize;
		}
		//Get Shared Texture
		private void GetResolution()
		{
			string resolution = mPlugin.Call<string>("GetResolution");
			string[] res = resolution.Split('x');
			mTexWidth = int.Parse(res[0]);
			mTexHeight = int.Parse(res[1]);
		}
		private IntPtr GetTexturePtr()
		{
			return !IsInitialized ? IntPtr.Zero : JNIUtil.GetTexturePtr((int)mRawObject);
		}
		public void UpdateFrame()
		{
			if (!IsInitialized)
				return;
			if (SystemInfo.renderingThreadingMode == UnityEngine.Rendering.RenderingThreadingMode.MultiThreaded)
			{
				GL.IssuePluginEvent(JNIUtil.UpdateSurfaceFunc(), (int)mRawObject);
			}
			else
			{
				JNIUtil.UpdateSurface((int)mPlugin.GetRawObject());
			}
			IntPtr newPtr = GetTexturePtr();
			IntPtr oldPtr = mRawImage.texture.GetNativeTexturePtr();

			if ((newPtr != IntPtr.Zero) && (newPtr != oldPtr))
			{
				((Texture2D)mRawImage.texture).UpdateExternalTexture(newPtr);
			}
		}
		private void Update()
		{
			try
			{
				UpdateFrame();
			}
			catch (Exception e)
			{
				Debug.LogError(mTag + "UpdateFrame failed:" + e.Message);
				MessageManager.Instance.Error("UpdateFrame failed:" + e.Message);
				enabled = false;
			}
		}
		private void SaveLastApp()
		{
			if (mPlugin == null)
				return;
			var raw = mPlugin.Call<string>("GetShortcut");
			Debug.Log(mTag + "SaveLastApp:" + raw);
			PlayerPrefs.SetString("LastApp", raw);
		}
	}
}
