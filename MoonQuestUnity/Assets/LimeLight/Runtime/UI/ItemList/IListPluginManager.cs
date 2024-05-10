using UnityEngine;
namespace PCP.LibLime
{
	public interface IListPluginManager
	{
		public string GetRawlist(bool choice);
		public GameObject ListItemPrefab { get; }
		public Transform ListParent { get; }
	}
}
