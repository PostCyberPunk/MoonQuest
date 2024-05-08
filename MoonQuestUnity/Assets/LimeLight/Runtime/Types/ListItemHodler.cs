using TMPro;
using UnityEngine;
namespace PCP.LibLime
{
	public class PCListItemHodler : MonoBehaviour
	{
		private TMP_Text mText;
		private GameObject mPairIcon;
		private ComputerData mData;
		private PcManager mManager;
		public string GetUUID() => mData.uuid;
		private void Awake()
		{
			mText = GetComponentInChildren<TMP_Text>();
			mPairIcon = transform.GetChild(1).gameObject;
		}
		internal void UpdateItem(ComputerData data, PcManager m)
		{
			mData = data;
			mManager = m;
			gameObject.name = data.uuid;
			mText.text = data.name;
			mPairIcon.SetActive(data.pairState != PairState.PAIRED);
		}
		public void OnClick()
		{
			if (mManager == null)
			{

				Debug.LogError("ItemOnClick :PcManager Not Found");
				return;
			}
			if (mData.pairState != PairState.PAIRED)
			{
				mManager.PairComputer(mData.uuid);
			}
			else
			{
				mManager.StartAppList(mData.uuid);
			}
		}
	}
}



