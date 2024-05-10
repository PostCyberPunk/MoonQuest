using TMPro;
using UnityEngine;

namespace PCP.LibLime
{
	public class NvAppListItemHodler : MonoBehaviour, IListHolder<NvAppData, AppManager, int>
	{
		[SerializeField] private TMP_Text mText;
		public TMP_Text TitleText { get => mText; set => mText = value; }
		private NvAppData mData;
		private AppManager mManager;
		public int GetID() => mData.appId;
		public void UpdateItem(NvAppData data, AppManager m)
		{
			mData = data;
			mManager = m;
			gameObject.name = data.appName;
			mText.text = data.appName;
		}
		public void OnClick()
		{
			if (mManager == null)
			{
				Debug.LogError("ItemOnClick :AppManager Not Found");
				return;
			}
			//TODO: add hint here
			if (!mData.initialized)
			{
				return;
			}
			mManager.StartApp(mData.appId);
		}
	}
}

