using TMPro;
using UnityEngine;
namespace PCP.PostSpace.UI
{
	public class PalmMenuActivator : MonoBehaviour
	{
		[SerializeField] private GameObject mMenu;
		[SerializeField] private float maxPalmDistance = .4f;
		/* [SerializeField] private float maxPalmAngle = 45; */
		[SerializeField] private float maxHeadFOV = 45;

		[SerializeField] private Transform mPalm;
		[SerializeField] private Transform mCam;
		[SerializeField] private GrabMenu[] mGrabMenus;
		[SerializeField] private TMP_Text mInfoText;
		public void Update()
		{
			float distance = Vector3.Distance(mPalm.position, mCam.position);
			/* float palmAngle = Vector3.Angle(mPalm.up, Vector3.up); */
			/* float headPalmAngle = Vector3.Angle(mPalm.position - mCam.position, mCam.forward); */
			/* mMenu.SetActive(distance < maxPalmDistance && palmAngle < maxPalmAngle && headPalmAngle < maxHeadFOV); */
			float headPalmAngle = Vector3.Angle(mPalm.up, mCam.forward * -1);
			mMenu.SetActive(distance < maxPalmDistance && headPalmAngle < maxHeadFOV);
			mMenu.transform.SetPositionAndRotation(mPalm.position, mPalm.rotation);
		}
	}
}
