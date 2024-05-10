using System;
namespace PCP.LibLime
{
	[Serializable]
	public struct ComputerData : IIdProvider<string>
	{
		public string uuid;
		public string name;
		public ComputerState state;
		public PairState pairState;
		public readonly string GetID() => uuid;
	}
	[Serializable]
	internal struct ComputerDataWrapper
	{
		public ComputerData[] data;
	}
}



