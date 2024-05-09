using TMPro;
using UnityEngine;
namespace PCP.LibLime
{
	public class PCListItemHodler : MonoBehaviour
	{
		[SerializeField] private TMP_Text mText;
		[SerializeField] private GameObject mPairIcon;
		private ComputerData mData;
		private PcManager mManager;
		public string GetUUID() => mData.uuid;
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
			if (mData.state != ComputerState.ONLINE)
			{
				return;
			}
			if (mData.pairState == PairState.PAIRED)
			{
				mManager.StartAppList(mData.uuid);
			}
			else if (mData.pairState is not PairState.ALREADY_IN_PROGRESS)
			{
				mManager.PairComputer(mData.uuid);
			}
		}
	}
}



