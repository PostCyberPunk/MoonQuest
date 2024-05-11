using System.Collections.Generic;
using UnityEngine;

namespace PCP.LibLime
{
	public abstract class ListUpdater<T, U, V, W> where T : IIdProvider<V> where U : BasePluginBridge, IListPluginManager where W : MonoBehaviour, IListHolder<T, U, V>
	{
		protected readonly string mTag;
		protected readonly string mCmdPrefix;

		protected U mManager;
		protected GameObject ListItemPrefab;
		protected Transform ListParent;

		private readonly Dictionary<V, W> mItemMap = new();

		public ListUpdater(string tag, string cmdPrefix)
		{
			mTag = tag;
			mCmdPrefix = cmdPrefix;
		}

		private void RemoveItems(T[] list)
		{
			List<V> toRemove = new();
			foreach (T data in list)
			{
				V id = data.GetID();
				if (mItemMap.ContainsKey(id))
				{
					Object.Destroy(mItemMap[id].gameObject);
					toRemove.Add(id);
				}
				else
				{
					Debug.LogWarning(mTag + ":RemoveItem:Item not found:" + id);
				}
			}
			if (toRemove.Count == 0)
				return;
			foreach (V i in toRemove)
			{
				mItemMap.Remove(i);
			}
		}
		private void AddItems(T[] list)
		{
			foreach (T data in list)
			{
				V id = data.GetID();
				if (mItemMap.ContainsKey(id))
				{
					mItemMap[id].UpdateItem(data, mManager);
				}
				else
				{
					GameObject go = Object.Instantiate(ListItemPrefab, ListParent);
					go.GetComponent<W>().UpdateItem(data, mManager);
				}
			}

			//Update Item Dictionary
			if (ListParent == null)
				return;
			mItemMap.Clear();
			foreach (W child in ListParent.GetComponentsInChildren<W>())
			{
				mItemMap.Add(child.GetID(), child);
			}
		}
		private void UpdateList(char choice)
		{
			//Strarting Updtae List
			string rawList = mManager.GetRawlist(choice == '1');
			if (rawList == null || rawList == string.Empty)
				return;
			Debug.Log(mTag + ":RawList:" + rawList);
			T[] list;
			try
			{
				list = GetListFromJson(rawList);
			}
			catch (System.Exception e)
			{
				Debug.LogError(mTag + ":Error Parsing List:" + e.Message);
				return;
			}
			if (list == null)
			{
				Debug.LogError(mTag + ":Error Parsing List:List is null");
				return;
			}
			if (list.Length == 0)
			{
				Debug.LogWarning(mTag + ":No Computer need update");
				return;
			}
			if (choice == '0')
				RemoveItems(list);
			else
				AddItems(list);
		}
		protected abstract T[] GetListFromJson(string raw);
		public void UdpateListHandler(string m)
		{
			if (!m.StartsWith(mCmdPrefix))
				return;
			UpdateList(m[mCmdPrefix.Length]);
		}
		public void Clear()
		{
			mItemMap.Clear();
			foreach (Transform child in ListParent)
			{
				Object.Destroy(child.gameObject);
			}
		}
	}
}
