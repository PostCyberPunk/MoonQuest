using System;
namespace PCP.LibLime
{
	[Serializable]
	internal struct ComputerData
	{
		public string uuid;
		public string name;
		public ComputerState state;
		public PairState pairState;
	}
	[Serializable]
	internal struct ComputerDataWrapper
	{
		public ComputerData[] data;
	}
}



