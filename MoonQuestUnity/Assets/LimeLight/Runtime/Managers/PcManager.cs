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
			Type = LimePluginManager.PluginType.Pc;
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
			string url = AddPcDialog.GetComponentInChildren<TMP_InputField>().text;
			AddPcDialog.SetActive(false);
			mPlugin?.Call("AddComputerManually", url);
		}
		public void PairComputer(string uuid)
		{
			if (!enabled)
				return;
			PairingDialog.transform.GetChild(0).GetComponent<TMP_Text>().text = "";
			PairingDialog.SetActive(true);
			mCallBackHanlder += PairingHanlder;
			mPlugin?.Call("PairComputer", uuid);
		}
		public void StopPariring()
		{
			if (!enabled)
				return;
			mCallBackHanlder -= PairingHanlder;
			PairingDialog.SetActive(false);
		}
		public void StartAppList(string uuid)
		{
			if (!enabled)
				return;
			Blocker.SetActive(true);
			mPluginManager.StartManager(LimePluginManager.PluginType.App);
			if (uuid != "")
				mPlugin?.Call("StartAppList", uuid);
		}

		//Handlers///////////
		//TODO: maybe move this to a helper class or a interface
		private void UdpateListHandler(string m)
		{
			if (!m.StartsWith("pclist"))
				return;
			UpdateList(m[6]);
		}
		private void RemoveItems(ComputerData[] list)
		{
			List<string> toRemove = new();
			foreach (ComputerData data in list)
			{
				if (mComputerMap.ContainsKey(data.uuid))
				{
					Destroy(mComputerMap[data.uuid].gameObject);
					toRemove.Add(data.uuid);
				}
				else
				{
					Debug.LogWarning(mTag + ":RemoveItem:Item not found:" + data.uuid);
				}
			}
			if (toRemove.Count == 0)
				return;
			foreach (string i in toRemove)
			{
				mComputerMap.Remove(i);
			}
		}
		private void AddItems(ComputerData[] list)
		{
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
				mComputerMap.Add(child.GetID(), child);
			}
		}
		private void UpdateList(char choice)
		{
			//Strarting Updtae List
			string rawList = mPlugin.Call<string>("GetList", choice == '1');
			if (rawList == null || rawList == string.Empty)
				return;
			Debug.Log(mTag + ":RawList:" + rawList);
			ComputerData[] list;
			try
			{
				list = JsonUtility.FromJson<ComputerDataWrapper>(rawList).data;
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
		private void PairingHanlder(string msg)
		{
			if (msg.StartsWith("pairc"))
			{
				string result = msg[6..];
				switch (result)
				{
					case "0":
						PairingDialog.SetActive(false);
						mCallBackHanlder -= PairingHanlder;
						break;
					case "1":
						StartAppList("");
						mCallBackHanlder -= PairingHanlder;
						break;
					default:
						PairingDialog.transform.GetChild(0).GetComponent<TMP_Text>().text = result;
						break;
				}
			}
		}
		public void DumRemove()
		{
			mPlugin.Call("DumRemove");
		}
	}
}
