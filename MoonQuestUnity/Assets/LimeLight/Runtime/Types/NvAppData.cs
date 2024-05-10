using System;

namespace PCP.LibLime
{
	[Serializable]
	public struct NvAppData
	{
		public string appName;
		public int appId;
		public bool initialized;
	}
	[Serializable]
	internal struct NvAppDataWrapper
	{
		public NvAppData[] data;
	}
}

