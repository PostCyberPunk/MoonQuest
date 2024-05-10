using UnityEngine;

namespace PCP.LibLime
{
	public class NvAppLIstUpdater : ListUpdater<NvAppData, AppManager, int, NvAppListItemHodler>
	{
		public NvAppLIstUpdater(AppManager m, GameObject prefab, Transform parent) : base("NvAppLIstUpdater", "applist")
		{
			mManager = m;
			ListItemPrefab = prefab;
			ListParent = parent;
		}
		protected override NvAppData[] GetListFromJson(string raw)
		{
			return JsonUtility.FromJson<NvAppDataWrapper>(raw).data;
		}
	}
}
