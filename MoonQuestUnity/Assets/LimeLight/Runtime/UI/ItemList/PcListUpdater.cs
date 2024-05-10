using UnityEngine;
namespace PCP.LibLime
{
	public class PcListUpdater : ListUpdater<ComputerData, PcManager, string, PCListItemHodler>
	{
		public PcListUpdater(PcManager m, GameObject prefab, Transform parent) : base("PcListUpdater", "pclist")
		{
			mManager = m;
			ListItemPrefab = prefab;
			ListParent = parent;
		}

		protected override ComputerData[] GetListFromJson(string raw)
		{
			return JsonUtility.FromJson<ComputerDataWrapper>(raw).data;
		}
	}
}
