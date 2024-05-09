using System.Collections;
using TMPro;
using UnityEngine;

public class Notification : MonoBehaviour
{
	private static readonly float displayTime = 3f;
	private static readonly float fadeTime = 1f;
	private TMP_Text textComponent;
	private CanvasGroup canvasGroup;

	private void Awake()
	{
		textComponent = GetComponentInChildren<TMP_Text>();
		canvasGroup = GetComponent<CanvasGroup>();
	}

	public void Display(string message)
	{
		textComponent.text = message;
		StartCoroutine(DisplayAndFade());
	}

	private IEnumerator DisplayAndFade()
	{
		float timer = 0f;
		while (timer < displayTime)
		{
			timer += Time.deltaTime;
			yield return null;
		}

		timer = 0f;
		while (timer < fadeTime)
		{
			canvasGroup.alpha = Mathf.Lerp(1f, 0f, timer / fadeTime);
			timer += Time.deltaTime;
			yield return null;
		}

		canvasGroup.alpha = 1f;
		gameObject.SetActive(false);
	}
}
