using System;
using UnityEngine;
using UnityEngine.UI;
namespace PCP.LibLime
{
	public class StreamManager : BasePluginBride
	{
		private RawImage mRawImage;
		private int mTexWidth;
		private int mTexHeight;
		private IntPtr mRawObject;

		public void Init(AndroidJavaObject o, RawImage img, int w, int h)
		{
			mTexWidth = w;
			mTexHeight = h;
			mRawImage = img;
			mTag = "StreamManager";
			Init(o);
		}

		protected override void OnCreate()
		{
			mRawObject = mPlugin.GetRawObject();
			mRawImage.rectTransform.sizeDelta = new Vector2(mTexWidth, mTexHeight);
			mRawImage.texture = new Texture2D(mTexWidth, mTexHeight, TextureFormat.ARGB32, false, false)
			{
				filterMode = FilterMode.Trilinear,
				anisoLevel = 16
			};
		}

		protected override void OnDestroy()
		{
			base.OnDestroy();
			//TODO: Need to Fix this;
			mRawImage.texture = null;
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
			UpdateFrame();
		}
	}
}
