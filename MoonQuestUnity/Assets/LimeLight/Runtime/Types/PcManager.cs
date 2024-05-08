using System.Collections.Generic;
using TMPro;
using UnityEngine;
namespace PCP.LibLime
{
	public class PcManager : BasePluginBride
	{
		public GameObject ListItemPrefab;
		public Transform ListParent;
		public GameObject PairingDialog;
		public GameObject AddPcDialog;
		//PERF: maybe add item pool;
		private readonly Dictionary<string, PCListItemHodler> mComputerMap = new();
		private void Awake()
		{
			mTag = "PcManger";
		}

		protected override void OnCreate()
		{
			/* UpdateList(); */
			mCallBackHanlder += UdpateListHandler;
		}
		protected override void OnStop()
		{
			//Cleear CallBack
			mCallBackHanlder = null;
			//Close Dialogs
			PairingDialog.SetActive(false);
			AddPcDialog.SetActive(false);
			//Clear Item List
			mComputerMap.Clear();
			foreach (Transform child in ListParent)
			{
				Destroy(child.gameObject);
			}
		}

		//Bridge///////////
		public void AddComputerManually()
		{
			if (!enabled)
				return;
			string url = AddPcDialog.GetComponent<TMP_InputField>().text;
			mPlugin?.Call("AddComputerManually", url);
		}
		public void PairComputer(string uuid)
		{
			if (!enabled)
				return;
			mPlugin?.Call("PairComputer", uuid);

		}
		public void StartAppList(string uuid)
		{
			if (!enabled)
				return;
			Blocker.SetActive(true);
			mCallBackHanlder += ChangeUIhandler;
			mPlugin?.Call("StartAppList", uuid);
		}

		//Handlers///////////
		private void ChangeUIhandler(string m)
		{
			if (m != "pcdone1")
				return;
			/* if (!m.StartsWith("pcdone")) */
			/* 	return; */
			/* if (m[^1] != '1') */
			/* 	return; */
			enabled = false;
			Blocker.SetActive(false);
			mPluginManager.StartManager(LimePluginManager.PluginType.App);
			mCallBackHanlder -= ChangeUIhandler;
		}
		private void UdpateListHandler(string m)
		{
			if (!m.StartsWith("pclist"))
				return;
			UpdateList();
		}
		private void UpdateList()
		{
			//Strarting Updtae List
			string rawList = mPlugin.Call<string>("GetList");
			if (rawList == null || rawList == string.Empty)
				return;
			ComputerData[] list;
			try
			{
				list = JsonUtility.FromJson<ComputerData[]>(rawList);
			}
			catch (System.Exception e)
			{
				Debug.LogError(mTag + ":Error Parsing List" + e.Message);
				return;
			}

			if (list.Length == 0)
			{
				Debug.LogWarning(mTag + ":No Computer need update");
				return;
			}
			foreach (ComputerData data in list)
			{
				if (mComputerMap.ContainsKey(data.uuid))
				{
					mComputerMap[data.uuid].UpdateItem(data, this);
				}
				else
				{
					GameObject go = Instantiate(ListItemPrefab, ListParent);
					go.GetComponent<PCListItemHodler>().UpdateItem(data, this);
				}
			}

			//Update Item Dictionary
			if (ListParent == null)
				return;
			mComputerMap.Clear();
			foreach (PCListItemHodler child in ListParent.GetComponentsInChildren<PCListItemHodler>())
			{
				mComputerMap.Add(child.GetUUID(), child);
			}
		}
	}
}
