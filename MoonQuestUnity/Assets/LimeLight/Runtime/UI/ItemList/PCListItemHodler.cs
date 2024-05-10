using TMPro;
using UnityEngine;
namespace PCP.LibLime
{
	public class PCListItemHodler : MonoBehaviour, IListHolder<ComputerData, PcManager, string>
	{
		[SerializeField] private TMP_Text mText;
		public TMP_Text TitleText { get => mText; set => mText = value; }
		[SerializeField] private GameObject mPairIcon;
		private ComputerData mData;
		private PcManager mManager;

		public string GetID() => mData.uuid;
		public void UpdateItem(ComputerData data, PcManager m)
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



