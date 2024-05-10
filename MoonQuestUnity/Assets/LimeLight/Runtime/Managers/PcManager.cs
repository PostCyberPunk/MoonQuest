using TMPro;
using UnityEngine;
namespace PCP.LibLime
{
	public class PcManager : BasePluginBride, IListPluginManager
	{
		public GameObject PairingDialog;
		public GameObject AddPcDialog;

		//PERF: maybe add item pool;
		private PcListUpdater mListUpdater;
		[SerializeField] private Transform mListParent;
		[SerializeField] private GameObject mListItemPrefab;
		public GameObject ListItemPrefab => mListItemPrefab;
		public Transform ListParent => mListParent;

		private void Awake()
		{
			Type = LimePluginManager.PluginType.Pc;
			mTag = "PcManger";
			mListUpdater = new PcListUpdater(this, mListItemPrefab, mListParent);
		}

		protected override void OnCreate()
		{
			/* UpdateList(); */
			mCallBackHanlder += mListUpdater.UdpateListHandler;
		}
		protected override void OnStop()
		{
			//Cleear CallBack
			mCallBackHanlder = null;
			//Close Dialogs
			PairingDialog.SetActive(false);
			AddPcDialog.SetActive(false);
			//Clear Item List
			mListUpdater.Clear();
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
		//List
		public string GetRawlist(bool choice)
		{
			return mPlugin.Call<string>("GetList", choice);
		}
		public void DumRemove()
		{
			mPlugin.Call("DumRemove");
		}
	}
}
