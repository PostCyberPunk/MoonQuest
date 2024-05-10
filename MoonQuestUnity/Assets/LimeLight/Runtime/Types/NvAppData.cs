using System;

namespace PCP.LibLime
{
	[Serializable]
	public struct NvAppData : IIdProvider<int>
	{
		public string appName;
		public int appId;
		public bool initialized;
		public readonly int GetID() => appId;
	}
	[Serializable]
	internal struct NvAppDataWrapper
	{
		public NvAppData[] data;
	}
}

