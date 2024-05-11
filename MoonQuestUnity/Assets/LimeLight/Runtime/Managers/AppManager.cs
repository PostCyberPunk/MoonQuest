using UnityEngine;

namespace PCP.LibLime
{
	public class AppManager : BasePluginBridge, IListPluginManager
	{

		private NvAppLIstUpdater mListUpdater;
		[SerializeField] private Transform mListParent;
		[SerializeField] private GameObject mListItemPrefab;
		public GameObject ListItemPrefab => mListItemPrefab;
		public Transform ListParent => mListParent;
		private void Awake()
		{
			Type = LimePluginManager.PluginType.App;
			mTag = "AppManger";
			mListUpdater = new NvAppLIstUpdater(this, mListItemPrefab, mListParent);
		}
		protected override void OnCreate()
		{
			//update app list once at start
			mListUpdater.UdpateListHandler("applist1");
			mCallBackHanlder += mListUpdater.UdpateListHandler;
		}
		protected override void OnStop()
		{
			//Cleear CallBack
			mCallBackHanlder = null;
			//Clear Item List
			mListUpdater.Clear();
		}

		public void StartApp(int appid)
		{
			if (!enabled)
				return;
			Blocker.SetActive(true);
			mPluginManager.StartManager(LimePluginManager.PluginType.Stream);
			mPlugin?.Call("StartApp", appid);
		}

		public string GetRawlist(bool choice)
		{
			return mPlugin.Call<string>("GetList", choice);
		}
	}
}
