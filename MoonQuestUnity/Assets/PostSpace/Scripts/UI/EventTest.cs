using UnityEngine;

public class EventTest : MonoBehaviour
{
	public void OnSelect()
	{
		MessageManager.Instance.Warn("Selected");
	}
	public void OnUnselect()
	{
		MessageManager.Instance.Error("UnSelected");
	}
	public void OnHover()
	{
		MessageManager.Instance.Info("Hovered");
	}
	public void OnUnhover()
	{
		MessageManager.Instance.Warn("UnHovered");
	}
}
