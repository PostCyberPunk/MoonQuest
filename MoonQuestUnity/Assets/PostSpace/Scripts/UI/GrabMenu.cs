using System.Collections;
using Oculus.Interaction;
using UnityEngine;

namespace PCP.PostSpace.UI
{
	public class GrabMenu : MonoBehaviour
	{
		[SerializeField] private GameObject mMenu;
		[SerializeField] private Transform tempP;
		private Transform mParent;
		private Vector3 oriPos;
		private Vector3 oriRot;

		[SerializeField] private InteractableUnityEventWrapper EventWrapper;
		[SerializeField] private MeshRenderer TargetRenderer;
		[SerializeField] private GameObject Indicator;

		private void Awake()
		{
			mParent = transform.parent;
			oriPos = transform.localPosition;
			oriRot = transform.localEulerAngles;

			EventWrapper.WhenSelect.AddListener(OnSelect);
			EventWrapper.WhenUnselect.AddListener(OnUnselect);
		}
		private void OnSelect()
		{
			//move the object to root to avoid being disable
			Indicator.SetActive(true);
			transform.SetParent(tempP);
			ChangeColor(true);
		}
		private void OnUnselect()
		{
			Indicator.SetActive(false);
			ActiveMenu();
			//move the object back to its parent
			ChangeColor(false);
			StartCoroutine(ResetPos());
		}

		private void ChangeColor(bool isSelected)
		{
			TargetRenderer.material.color = isSelected ? Color.green : Color.gray;
		}
		private void ActiveMenu()
		{
			mMenu.transform.position = transform.position;
			mMenu.transform.forward = Camera.main.transform.forward;
			mMenu.SetActive(true);
		}
		private IEnumerator ResetPos()
		{
			yield return new WaitForEndOfFrame();
			transform.SetParent(mParent);
			transform.localPosition = oriPos;
			transform.localEulerAngles = oriRot;
		}
	}
}

