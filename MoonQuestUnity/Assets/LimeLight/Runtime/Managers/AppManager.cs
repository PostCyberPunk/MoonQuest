using System;
using System.Collections.Generic;
using UnityEngine;

namespace PCP.LibLime
{
	public class AppManager : BasePluginBride
	{
		public GameObject ListItemPrefab;
		public Transform ListParent;
		private readonly Dictionary<int, NvAppListItemHodler> mAppMap = new();
		private void Awake()
		{
			Type = LimePluginManager.PluginType.App;
			mTag = "AppManger";
		}
		protected override void OnCreate()
		{
			UpdateList();
			mCallBackHanlder += UdpateListHandler;
		}
		protected override void OnStop()
		{
			//Cleear CallBack
			mCallBackHanlder = null;
			//Clear Item List
			mAppMap.Clear();
			foreach (Transform child in ListParent)
			{
				Destroy(child.gameObject);
			}
		}

		public void StartApp(int appid)
		{
			if (!enabled)
				return;
			Blocker.SetActive(true);
			mPluginManager.StartManager(LimePluginManager.PluginType.Stream);
			mPlugin?.Call("StartApp", appid);
		}

		private void UdpateListHandler(string m)
		{
			if (!m.StartsWith("applist"))
				return;
			UpdateList();
		}
		private void UpdateList()
		{
			//Strarting Updtae List
			string rawList = mPlugin.Call<string>("GetList");
			if (rawList == null || rawList == string.Empty)
				return;
			Debug.Log(mTag + ":RawList:" + rawList);
			NvAppData[] list;
			try
			{
				list = JsonUtility.FromJson<NvAppDataWrapper>(rawList).data;
			}
			catch (Exception e)
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
				Debug.LogWarning(mTag + ":No NvApp need update");
				return;
			}
			foreach (NvAppData data in list)
			{
				if (mAppMap.ContainsKey(data.appId))
				{
					mAppMap[data.appId].UpdateItem(data, this);
				}
				else
				{
					GameObject go = Instantiate(ListItemPrefab, ListParent);
					go.GetComponent<NvAppListItemHodler>().UpdateItem(data, this);
				}
			}

			//Update Item Dictionary
			if (ListParent == null)
				return;
			mAppMap.Clear();
			foreach (NvAppListItemHodler child in ListParent.GetComponentsInChildren<NvAppListItemHodler>())
			{
				mAppMap.Add(child.GetAppID(), child);
			}
		}
	}
}
